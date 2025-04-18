package kr.recommendsystem.service

import kotlinx.coroutines.*
import kr.recommendsystem.repository.UserActionRecordRepository
import org.springframework.stereotype.Service
import kotlin.math.sqrt

/**
 * 공통 데이터 준비
 * @property posts 사용자별 이미 상호작용한 게시글 집합
 * @property vectors 사용자별 게시글 가중치 벡터
 * @property norms 사용자별 벡터의 노름
 */
private data class CalculatorData(
    val posts: Map<Long, List<Long>>,
    val vectors: Map<Long, Map<Long, Double>>,
    val norms: Map<Long, Double>
)

@Service
class RecommendPostService(
    private val weightQueryRepository: UserActionRecordRepository
) {
    private final val topNSimilarity = 100
    private final val topNPosts = 500

    suspend fun getRecommendPosts(userId: Long) {
        val handler = CoroutineExceptionHandler { _, e ->
            // 예외 처리 로직
        }

        // TODO: 사용자 ID를 기준으로 이미 데이터가 있는 경우 or 게시글과 상호작용이 없는 사용자에 경우 생략
        coroutineScope {
            launch(Dispatchers.Default + handler) {
                executeRecommendationJob(userId = userId)
            }
        }
    }

    private suspend fun executeRecommendationJob(userId: Long) {
        val weights = withContext(Dispatchers.IO) {
            weightQueryRepository.findAllWeights()
        }

        val data = prepareUserData(weights)

        val similarity = calculatorUserSimilarities(userId, data)

        calculatorRecommendations(similarity, userId, data)
    }

    /**
     * 공통 데이터 준비:
     * - 사용자별 이미 상호작용한 게시글 집합 (userPosts)
     *     - UserId -> Set<PostId>
     * - 사용자별 게시글 가중치 벡터 (userVectors)
     *     - UserId -> Map<PostId, Weight>
     * - 사용자별 벡터의 노름 (userNorms)
     *     - UserId -> Norm
     */
    private fun prepareUserData(weights: List<UserPostScore>): CalculatorData {
        val userPosts = weights
            .groupBy { it.userId }
            .mapValues { it.value.map { postScore -> postScore.postId } }

        val userVectors = weights
            .groupBy { it.userId }
            .mapValues { it.value.associate { postScore -> postScore.postId to postScore.weight } }

        val userNorms = userVectors
            .mapValues { sqrt(it.value.values.sumOf { score -> score * score }) }

        return CalculatorData(posts = userPosts, vectors = userVectors, norms = userNorms)
    }

    /**
     * ■ 동작 순서
     *   1) 기준 사용자의 벡터/노름을 구한다. (없으면 빈 리스트 즉시 반환)
     *   2) 비교 대상 ID 목록(otherUserIds)을 만든다. (없으면 빈 리스트 즉시 반환)
     *   3) 배치 크기 계산: 전체 유사도 개수 / (코어 수 * 4), 최소 1
     *   4) 각 사용자 쌍마다
     *        ‑ 대상 벡터·노름이 없으면 유사도 0 으로 단축(return@inner)
     *        ‑ 작은 맵만 순회해 dot‑product 계산 (교집합 Set 생성 최소화)
     *        ‑ normA·normB 가 0 이면 유사도 0, 아니면 dot/(normA*normB)
     *        ‑ UserSimilarity DTO 생성
     *   5) awaitAll + flatten 으로 모든 결과를 하나의 리스트로 결합해 반환.
     */
    private suspend fun calculatorUserSimilarities(
        userId: Long, data: CalculatorData
    ): List<UserSimilarity> = coroutineScope {
        // 1) 기준 벡터/노름 준비
        val baseVector = data.vectors[userId] ?: return@coroutineScope emptyList()
        val baseNorm = data.norms[userId] ?: 0.0

        // 2) 비교 대상 ID 목록(otherUserIds)을 만든다.
        val otherUserIds = data.vectors.keys.filter { it != userId }
        if (otherUserIds.isEmpty()) return@coroutineScope emptyList()

        // 3) 배치 크기 선정
        val coreCount = Runtime.getRuntime().availableProcessors()
        val batchSize = maxOf(1, otherUserIds.size / (coreCount * 4))

        // 4) 병렬 유사도 계산
        otherUserIds
            .chunked(batchSize)
            .map { batch ->
                async {
                    batch.map inner@{ otherUserId ->
                        // 4-1) 대상 백터/노름 가져오기
                        val targetVector = data.vectors[otherUserId] ?: return@inner UserSimilarity(
                            baseUserId = userId, targetUserId = otherUserId, similarity = 0.0
                        )
                        val targetNorm = data.norms[otherUserId] ?: 0.0
                        // 4-2) 교집합을 구하고 내적 계산
                        val dotProduct = baseVector.keys
                            .intersect(targetVector.keys)
                            .sumOf { postId -> baseVector[postId]!! * targetVector[postId]!! }

                        // 4-3) 코사인 유사도 계산
                        val similarity = if (baseNorm == 0.0 || targetNorm == 0.0) 0.0
                        else dotProduct / (baseNorm * targetNorm)

                        // 4-4) 결과 객체 생성
                        UserSimilarity(baseUserId = userId, targetUserId = otherUserId, similarity = similarity)
                    }
                }
            }
            // 5) 모든 async 결과를 합쳐서 반환
            .awaitAll().flatten()
    }

    /**
     * ■ 동작 순서
     *   1) 유사도 내림차순 정렬 후 상위 K개 선택
     *   2) 이미 상호작용한 게시글 ID 구하기
     *   3) 배치 크기 계산: 전체 유사도 개수 / (코어 수 * 4), 최소 1
     *   4) Recommendation 생성
     *        ‑ 대상 사용자의 벡터 가져오기 (없으면 빈 리스트)
     *        ‑ 이미 상호작용한 게시글 제외
     *        ‑ Recommendation 객체 생성
     *   5) (userId, postId) 기준으로 점수 합산 후 내림차순 정렬 및 topNPosts 개 반환
     */
    private suspend fun calculatorRecommendations(
        similarities: List<UserSimilarity>, userId: Long, data: CalculatorData
    ): List<Recommendation> = coroutineScope {
        // 1) 유사도 내림차순 정렬 후 상위 K개 선택
        val sortSimilarities = similarities
            .sortedByDescending { it.similarity }
            .take(topNSimilarity)

        // 2) 이미 상호작용한 게시글 ID 구하기
        val interactedPostIds = data.posts[userId] ?: emptyList()

        // 배치 크기 계산: 전체 유사도 개수 / (코어 수 * 4), 최소 1
        val coreCount = Runtime.getRuntime().availableProcessors()
        val batchSize = maxOf(1, sortSimilarities.size / (coreCount * 4))

        // 3) Recommendation 생성
        val recommendations = sortSimilarities
            .chunked(batchSize)
            .map { batch ->
                async {
                    batch.flatMap { similarity ->
                        // 3-1) 대상 사용자의 벡터 가져오기
                        val vector =
                            data.vectors[similarity.targetUserId] ?: return@flatMap emptyList<Recommendation>()

                        // 3-2) 이미 상호작용한 게시글 제외
                        val filtered = vector.filterKeys { it !in interactedPostIds }

                        // 3-3) Recommendation 객체 생성
                        filtered.map { (postId, weight) ->
                            Recommendation(
                                userId = userId, postId = postId, score = similarity.similarity * weight
                            )
                        }
                    }
                }
            }
            // 4) 모든 async 결과를 합쳐서 평탄화
            .awaitAll().flatten()

        // 5) (userId, postId) 기준으로 점수 합산 후 내림차순 정렬 및 topNPosts 개 반환
        recommendations
            .groupBy { it.userId to it.postId }
            .map { (key, recs) ->
                Recommendation(userId = key.first, postId = key.second, score = recs.sumOf { it.score })
            }
            .sortedByDescending { it.score }
            .take(topNPosts)
            .toList()
    }
}
