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
        CoroutineScope(Dispatchers.Default).launch {
            executeRecommendationJob()
        }
    }

    private suspend fun executeRecommendationJob() {
        var weights: List<UserPostScore>
        val executionTime1 = measureNanoTime {
            weights = calculateWeight()
        }
        println("calculateWeight() 실행 시간: ${executionTime1 / 1_000_000} ms")

        val executionTime2 = measureNanoTime {
            prepareUserData(weights)
        }
        println("prepareUserData() 실행 시간: ${executionTime2 / 1_000_000} ms")

        var similarity: List<UserSimilarity>
        val executionTime3 = measureNanoTime {
            similarity = computeUserSimilaritiesEfficientCoroutine(weights)
        }
        println("computeUserSimilaritiesEfficientCoroutine() 실행 시간: ${executionTime3 / 1_000_000} ms")


        var recommendation = listOf<Recommendation>()
        val executionTime4 = measureNanoTime {
            recommendation = generateRecommendationsCoroutine(weights, similarity)
        }
        println("computeUserSimilaritiesEfficientCoroutine() 실행 시간: ${executionTime4 / 1_000_000} ms")

        println("recommendation.size = ${recommendation.size}")
    }

    /**
     * 공통 데이터 준비:
     * - 사용자별 이미 상호작용한 게시글 집합 (userPosts)
     * - 사용자별 게시글 가중치 벡터 (userVectors)
     * - 사용자별 벡터의 노름 (userNorms)
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
     * 사용자 별 게시글에 대한 가중치 계산
     */
    fun calculateWeight(): List<UserPostScore> {
        return weightQueryRepository.findAllWeights()
    }

    suspend fun computeUserSimilaritiesEfficientCoroutine(weights: List<UserPostScore>): List<UserSimilarity> =
        coroutineScope {
            // 1) 역색인 구축: postId -> List of (userId, weight)
            val invertedIndex = mutableMapOf<Long, MutableList<Pair<Long, Double>>>()
            for (rec in weights) {
                invertedIndex
                    .computeIfAbsent(rec.postId) { mutableListOf() }
                    .add(rec.userId to rec.weight)
            }

            // 2) 전역 ConcurrentHashMap 하나로 dotProducts 누적
            val dotProducts = ConcurrentHashMap<Pair<Long, Long>, Double>()

            // 3) 게시글별 내적 기여를 병렬 누적
            val entries = invertedIndex.values.toList()
            withContext(Dispatchers.Default) {
                // chunk size 조절: 코어 수와 데이터 크기에 따라 튜닝 가능
                val nCores = Runtime.getRuntime().availableProcessors()
                val chunkSize = maxOf(1, entries.size / (nCores * 4))

                entries
                    .chunked(chunkSize)
                    .map { chunk ->
                        async {
                            for (userList in chunk) {
                                if (userList.size <= 1) continue
                                for (i in 0 until userList.size) {
                                    for (j in i + 1 until userList.size) {
                                        val (uA, wA) = userList[i]
                                        val (uB, wB) = userList[j]
                                        val key = if (uA < uB) uA to uB else uB to uA
                                        dotProducts.compute(key) { _, old ->
                                            (old ?: 0.0) + wA * wB
                                        }
                                    }
                                }
                            }
                        }
                    }
                    .awaitAll()
            }

            // 4) dotProducts에 저장된 내적을 이용해 코사인 유사도 계산
            val similarities = mutableListOf<UserSimilarity>()
            for ((pair, dot) in dotProducts) {
                val (u1, u2) = pair
                val norm1 = userNorms[u1] ?: 0.0
                val norm2 = userNorms[u2] ?: 0.0
                val sim = if (norm1 == 0.0 || norm2 == 0.0) 0.0 else dot / (norm1 * norm2)
                similarities += UserSimilarity(u1, u2, sim)
            }

            similarities
        }

    suspend fun generateRecommendationsCoroutine(
        weights: List<UserPostScore>, similarities: List<UserSimilarity>, topK: Int = 50        // 상위 50명 이웃만 사용한다고 가정
    ): List<Recommendation> = coroutineScope {
        // 1) Top‑K 이웃만 남긴 similarityMap 구성 (양방향)
        val similarityMap: Map<Long, List<Pair<Long, Double>>> = similarities
            .flatMap { sim ->
                listOf(
                    sim.userId1 to (sim.userId2 to sim.similarity),
                    sim.userId2 to (sim.userId1 to sim.similarity)
                )
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, neighList) ->
                neighList
                    .sortedByDescending { it.second }
                    .take(topK)
            }

        // 2) 병렬로 사용자별 추천 점수 계산
        userVectors.keys.map { userId ->
            async(Dispatchers.Default) {
                val seen = userPosts[userId] ?: emptySet()
                val rec = mutableMapOf<Long, Double>()

                // 상위 K 이웃만 반복
                for ((otherId, sim) in similarityMap[userId] ?: emptyList()) {
                    val vec = userVectors[otherId] ?: continue
                    for ((postId, weight) in vec) {
                        if (postId in seen) continue
                        rec.merge(postId, sim * weight) { old, new -> old + new }
                    }
                }

                // 정렬 + DTO 매핑
                val list = rec.entries
                    .sortedByDescending { it.value }
                    .take(100)
                    .map { (postId, score) ->
                        Recommendation(userId, postId)
                    }
                userId to list
            }
        }
            .awaitAll()
            .toMap()
            .flatMap { it.value }
    }
}
