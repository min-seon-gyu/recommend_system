package kr.recommendsystem.scheduler

import kotlinx.coroutines.*
import kr.recommendsystem.repository.PostRepository
import kr.recommendsystem.repository.RecommendPostRepository
import kr.recommendsystem.repository.UserActionRecordRepository
import kr.recommendsystem.repository.UserRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sqrt
import kotlin.system.measureNanoTime

@Component
class RecommendScheduler(
    private val weightQueryRepository: UserActionRecordRepository,
    private val recommendPostRepository: RecommendPostRepository,
    private val userRepository: UserRepository,
    private val postRepository: PostRepository
) {

    /** 사용자별 이미 상호작용한 게시글 집합 **/
    private lateinit var userPosts: Map<Long, Set<Long>>

    /** 사용자별 게시글 가중치 벡터 **/
    private lateinit var userVectors: Map<Long, Map<Long, Double>>

    /** 사용자별 벡터의 노름 **/
    private lateinit var userNorms: Map<Long, Double>

    /**
     * 추천 게시글 저장을 위한 스케줄러
     * 매일 03:00에 실행
     */
    @Scheduled(cron = "0 0 3 * * *")
    fun calculateSimilarity() {
        val userId = 10001L

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
        println("weightQueryRepository.findAllWeights() 실행 시간: ${executionTime1 / 1_000_000} ms")

        val executionTime2 = measureNanoTime {
            prepareUserData(weights)
        }
        println("prepareUserData() 실행 시간: ${executionTime2 / 1_000_000} ms")

        var similarity: List<UserSimilarity>
        val executionTime3 = measureNanoTime {
            similarity = calculatorUserSimilarities(weights, userId)
        }
        println("calculatorUserSimilarities() 실행 시간: ${executionTime3 / 1_000_000} ms")


        var recommendation: List<Recommendation>
        val executionTime4 = measureNanoTime {
            recommendation = calculatorRecommendations(similarity, userId)
        }
        println("calculatorRecommendations() 실행 시간: ${executionTime4 / 1_000_000} ms")

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
            .mapValues { entry -> entry.value.map { it.postId }.toSet() }

        userVectors = weights
            .groupBy { it.userId }
            .mapValues { entry ->
                entry.value.associate { it.postId to it.weight }
            }

        userNorms = userVectors
            .mapValues { (_, vec) ->
                sqrt(vec.values.sumOf { it * it })
            }
    }

    /**
     * 특정 사용자(userId)와 다른 사용자들 사이의 코사인 유사도를 계산합니다.
     *
     * 1) 사용자-게시글 벡터 전체 구성
     * 2) 비교 대상 사용자 목록 추출 (자기 자신 제외)
     * 3) CPU 바운드 작업을 Dispatchers.Default에서 병렬 실행
     *    3-1) 코어 수 기반으로 배치 크기 계산
     *    3-2) 사용자 ID 목록을 배치 단위로 분할
     *    3-3) 각 배치에서
     *         - 공통 게시글에 대한 내적(dot product) 계산
     *         - 벡터 노름을 사용해 코사인 유사도 계산
     *         - 결과를 리스트에 추가
     *    3-4) 모든 비동기 작업 완료 대기
     * 4) 최종 유사도 리스트 반환
     */
    suspend fun calculatorUserSimilarities(weights: List<UserPostScore>, userId: Long): List<UserSimilarity> {

        /** 자기 자신을 제외하고, 게시글과 상호작용을 한 유저의 ID Set**/
        val otherUserIds = userVectors.keys.filter { it != userId }.toSet()

        /** 병렬로 코사인 유사도 계산 **/
        return coroutineScope {
            withContext(Dispatchers.Default) {
                val coreCount = Runtime.getRuntime().availableProcessors()
                val batchSize = maxOf(1, otherUserIds.size / (coreCount * 4))

                val deferred = otherUserIds
                    .chunked(batchSize)
                    .map { userBatch ->
                        async {
                            val baseVector = userVectors[userId]!!
                            val normA = userNorms[userId] ?: 0.0

                            userBatch.map { otherUserId ->
                                val targetVector = userVectors[otherUserId]!!
                                val normB = userNorms[otherUserId] ?: 0.0

                                /** 공통 게시글만 골라서 내적 계산 **/
                                val dot = baseVector.keys
                                    .intersect(targetVector.keys)
                                    .sumOf { postId -> baseVector[postId]!! * targetVector[postId]!! }

                                /** 코사인 유사도 계산 **/
                                val sim = if (normA == 0.0 || normB == 0.0) 0.0
                                else dot / (normA * normB)

                                UserSimilarity(baseUserId = userId, targetUserId = otherUserId, similarity = sim)
                            }
                        }
                    }
                deferred.awaitAll().flatten()
            }
        }
    }

    /**
     * 특정 사용자(userId)에 대해 유사도 기반으로 추천 게시글을 계산합니다.
     *
     * 절차:
     * 1) similarities를 유사도 내림차순 정렬 후 상위 topNSimilarity 명만 선별
     * 2) 대상 사용자가 이미 상호작용한 게시글 ID 집합 조회
     * 3) 추천 점수 누적용 ConcurrentHashMap(postScore) 초기화
     * 4) Dispatchers.Default 컨텍스트에서 병렬 처리
     *    4-1) CPU 코어 수 기반으로 배치 크기(batchSize) 계산
     *    4-2) 유사도 상위 K명 리스트를 batchSize 단위로 분할
     *    4-3) 각 배치(async)마다:
     *         - targetUserId의 게시글 벡터 순회
     *         - 이미 상호작용한 게시글은 건너뛰고
     *         - postScore.compute()로 similarity * weight 누적
     *    4-4) 모든 async 작업 완료 대기
     * 5) postScore의 엔트리를 점수 내림차순 정렬 후 상위 topNPosts 개 Recommendation 생성
     */
    private suspend fun calculatorRecommendations(
        similarities: List<UserSimilarity>, userId: Long, topNSimilarity: Int = 100, topNPosts: Int = 500
    ): List<Recommendation> {
        /** 유사도를 기준으로 내림차순으로 정렬 후 상위 K개 필터링 **/
        val sortSimilarities = similarities.sortedByDescending { it.similarity }.take(topNSimilarity)

        /** 이미 상호작용을 한 게시글 ID Set **/
        val interactedPostIds = userPosts[userId] ?: emptySet()

        /** 게시글의 추천 점수를 저장할 Map **/
        val postScore = ConcurrentHashMap<Long, Double>()

        /** 병렬로 추천 게시글 선정 **/
        coroutineScope {
            withContext(Dispatchers.Default) {
                val coreCount = Runtime.getRuntime().availableProcessors()
                val batchSize = maxOf(1, sortSimilarities.size / (coreCount * 4))

                sortSimilarities
                    .chunked(batchSize)
                    .map { userBatch ->
                        async {
                            /** 유사도 상위 K명을 기반으로 추천 게시글을 계산 **/
                            for ((baseUserId, targetUserId, similarity) in userBatch) {
                                val targetUserVector = userVectors[targetUserId] ?: continue
                                for ((postId, weight) in targetUserVector) {
                                    /** 추천 게시글이 이미 상호작용한 게시글인 경우 제외 **/
                                    if (postId in interactedPostIds) continue
                                    postScore.compute(postId) { _, value -> (value ?: 0.0) + similarity * weight }
                                }
                            }
                        }
                    }
                    .awaitAll()
            }
        }

        return postScore.entries
            .sortedByDescending { it.value }
            .take(topNPosts)
            .map { Recommendation(userId = userId, postId = it.key, score = it.value) }
    }
}

