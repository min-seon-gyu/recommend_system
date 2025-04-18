package kr.recommendsystem.service

import kotlinx.coroutines.*
import kr.recommendsystem.repository.UserActionRecordRepository
import org.springframework.stereotype.Service
import kotlin.math.sqrt
import kotlin.system.measureNanoTime

@Service
class RecommendPostService(
    private val weightQueryRepository: UserActionRecordRepository
) {

    private final val topNSimilarity = 100
    private final val topNPosts = 500

    /** 사용자별 이미 상호작용한 게시글 집합 **/
    private lateinit var userPosts: Map<Long, Set<Long>>

    /** 사용자별 게시글 가중치 벡터 **/
    private lateinit var userVectors: Map<Long, Map<Long, Double>>

    /** 사용자별 벡터의 노름 **/
    private lateinit var userNorms: Map<Long, Double>

    fun calculateSimilarity(userId: Long) {
        // TODO: 사용자 ID를 기준으로 이미 데이터가 있는 경우 or 게시글과 상호작용이 없는 사용자에 경우 생략
        CoroutineScope(Dispatchers.Default).launch {
            executeRecommendationJob(userId = userId)
        }
    }

    private suspend fun executeRecommendationJob(userId: Long) {
        var weights: List<UserPostScore>
        val executionTime1 = measureNanoTime {
            weights = weightQueryRepository.findAllWeights()
        }
        println("weightQueryRepository.findAllWeights() 실행 시간: ${executionTime1 / 1_000_000}ms")

        val executionTime2 = measureNanoTime {
            prepareUserData(weights)
        }
        println("prepareUserData() 실행 시간: ${executionTime2 / 1_000_000}ms")

        var similarity: List<UserSimilarity>
        val executionTime3 = measureNanoTime {
            similarity = calculatorUserSimilarities(userId)
        }
        println("calculatorUserSimilarities() 실행 시간: ${executionTime3 / 1_000_000}ms")


        var recommendation: List<Recommendation>
        val executionTime4 = measureNanoTime {
            recommendation = calculatorRecommendations(similarity, userId)
        }
        println("calculatorRecommendations() 실행 시간: ${executionTime4 / 1_000_000}ms")

        println("recommendation.size = ${recommendation.size}")
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
    private fun prepareUserData(weights: List<UserPostScore>) {
        userPosts = weights
            .groupBy { it.userId }
            .mapValues { it.value.map { postScore -> postScore.postId }.toSet() }

        userVectors = weights
            .groupBy { it.userId }
            .mapValues { it.value.associate { postScore -> postScore.postId to postScore.weight } }

        userNorms = userVectors
            .mapValues { sqrt(it.value.values.sumOf { score -> score * score }) }
    }

    /**
     * 특정 사용자(userId)와 다른 사용자들 간의 코사인 유사도를 계산합니다.
     *
     * 절차:
     * 1) 비교 대상 사용자 목록 구성 (자기 자신 제외)
     * 2) Dispatchers.Default 컨텍스트에서 병렬 처리
     *    a) CPU 코어 수 기반으로 batchSize 계산
     *    b) otherUserIds를 batchSize 단위로 분할
     *    c) 각 배치마다 async로 유사도 계산:
     *       - baseVector(기준 벡터)와 targetVector(비교 벡터) 조회
     *       - 공통 게시글에 대해 내적(dot product) 계산
     *       - 벡터 노름(norm)을 사용해 코사인 유사도 계산
     *       - UserSimilarity 객체 생성
     *    d) 모든 비동기 작업 완료 대기 및 결과 평탄화
     * 3) 최종 UserSimilarity 리스트 반환
     */
    private suspend fun calculatorUserSimilarities(userId: Long): List<UserSimilarity> {
        // 1) 자기 자신을 제외한 비교 대상 사용자 ID 집합
        val otherUserIds = userVectors.keys.filter { it != userId }.toSet()

        // 2) 병렬 코사인 유사도 계산
        return coroutineScope {
            withContext(Dispatchers.Default) {
                // a) 배치 크기 계산: 전체 유저 수 / (코어 수 * 4), 최소 1
                val coreCount = Runtime.getRuntime().availableProcessors()
                val batchSize = maxOf(1, otherUserIds.size / (coreCount * 4))

                // b) ID 목록을 배치로 분할 후 async 실행
                val deferred = otherUserIds
                    .chunked(batchSize)
                    .map { batch ->
                        async {
                            val baseVector = userVectors[userId]!!
                            val normA = userNorms[userId] ?: 0.0

                            batch.map { otherUserId ->
                                val targetVector = userVectors[otherUserId]!!
                                val normB = userNorms[otherUserId] ?: 0.0

                                // c.i) 공통 게시글 키로 내적(dot product) 계산
                                val dotProduct = baseVector.keys
                                    .intersect(targetVector.keys)
                                    .sumOf { postId ->
                                        baseVector[postId]!! * targetVector[postId]!!
                                    }

                                // c.ii) 코사인 유사도 계산 (0 벡터 처리)
                                val similarity = if (normA == 0.0 || normB == 0.0) 0.0
                                else dotProduct / (normA * normB)

                                // c.iii) 결과 객체 생성
                                UserSimilarity(baseUserId = userId, targetUserId = otherUserId, similarity = similarity)
                            }
                        }
                    }

                // d) 모든 async 결과 합쳐서 평탄화
                deferred.awaitAll().flatten()
            }
        }
    }

    /**
     * 특정 사용자(userId)에 대해 유사도 기반 추천 게시글을 계산합니다.
     *
     * 1) similarities를 유사도 내림차순으로 정렬 후 상위 topNSimilarity 개만 선택
     * 2) userId가 이미 상호작용한 게시글 ID 집합을 조회
     * 3) Dispatchers.Default 컨텍스트에서 병렬 처리 수행
     *    a) CPU 코어 수에 따라 batchSize 계산
     *    b) sortSimilarities를 batchSize 단위로 분할
     *    c) 각 배치마다 async로 추천 점수 생성:
     *       - targetUserId의 게시글 벡터 조회 (없으면 스킵)
     *       - 이미 상호작용한 게시글은 제외
     *       - similarity * weight 계산 후 Recommendation 객체 생성
     *    d) 모든 async 작업 완료 대기
     * 4) 생성된 Recommendation 리스트를 (userId, postId) 기준으로 합산
     * 5) 총합 점수 내림차순 정렬 후 상위 topNPosts 개 반환
     */
    private suspend fun calculatorRecommendations(
        similarities: List<UserSimilarity>, userId: Long
    ): List<Recommendation> {
        // 1) 유사도 내림차순 정렬 후 상위 K개 선택
        val sortSimilarities = similarities
            .sortedByDescending { it.similarity }
            .take(topNSimilarity)

        // 2) 이미 상호작용한 게시글 ID 집합
        val interactedPostIds = userPosts[userId] ?: emptySet()

        // 3) 병렬 처리로 Recommendation 생성
        val recommendations = coroutineScope {
            withContext(Dispatchers.Default) {
                // 배치 크기 계산: 전체 유사도 개수 / (코어 수 * 4), 최소 1
                val coreCount = Runtime.getRuntime().availableProcessors()
                val batchSize = maxOf(1, sortSimilarities.size / (coreCount * 4))

                // 유사도 리스트를 batchSize 단위로 분할 후 async 실행
                val deferred = sortSimilarities
                    .chunked(batchSize)
                    .map { batch ->
                        async {
                            batch.flatMap { similarity ->
                                // 3.c.i) 대상 사용자의 벡터 가져오기 (없으면 빈 리스트)
                                val vector = userVectors[similarity.targetUserId] ?: return@flatMap emptyList<Recommendation>()

                                // 3.c.ii) 이미 상호작용한 게시글 제외
                                val filtered = vector.filterKeys { it !in interactedPostIds }

                                // 3.c.iii) Recommendation 객체 생성
                                filtered.map { (postId, weight) ->
                                    Recommendation(
                                        userId = userId, postId = postId, score = similarity.similarity * weight
                                    )
                                }
                            }
                        }
                    }
                // 3.d) 모든 async 결과를 합쳐서 반환
                deferred.awaitAll().flatten()
            }
        }

        // 4) (userId, postId) 기준으로 점수 합산 후 5) 내림차순 정렬 및 topNPosts 개 반환
        return recommendations
            .groupBy { it.userId to it.postId }
            .map { (key, recs) ->
                Recommendation(userId = key.first, postId = key.second, score = recs.sumOf { it.score })
            }
            .sortedByDescending { it.score }
            .take(topNPosts)
            .toList()
    }
}
