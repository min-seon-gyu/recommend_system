package kr.recommendsystem.scheduler

import kr.recommendsystem.config.MeasureExecutionTime
import kr.recommendsystem.repository.UserActionRecordRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import kotlin.math.sqrt

@Component
class RecommendScheduler(
    private val weightQueryRepository: UserActionRecordRepository
) {

    private lateinit var userPosts: Map<Long, Set<Long>>
    private lateinit var userVectors: Map<Long, Map<Long, Double>>
    private lateinit var userNorms: Map<Long, Double>

    /**
     * 추천 게시글 저장을 위한 스케줄러
     * 매일 03:00에 실행
     */
    @Scheduled(cron = "0 0 3 * * *")
    @MeasureExecutionTime
    fun calculateSimilarity() {
        val weights = calculateWeight()

        prepareUserData(weights)

        val similarity = computeUserSimilaritiesEfficient(weights)

        val recommendation = generateRecommendations(weights, similarity)

        recommendation[1]!!.printPretty()
    }

    /**
     * 공통 데이터 준비:
     * - 사용자별 이미 상호작용한 게시글 집합 (userPosts)
     * - 사용자별 게시글별 가중치 벡터 (userVectors)
     * - 사용자별 벡터의 노름 (userNorms)
     */
    private fun prepareUserData(weights: List<UserPostScore>) {
        userPosts = weights
            .groupBy { it.userId }
            .mapValues { entry -> entry.value.map { it.postId }.toSet() }

        userVectors = weights
            .groupBy { it.userId }
            .mapValues { entry ->
                entry.value.associate { it.postId to it.weight.toDouble() }
            }
        userNorms = userVectors.mapValues { (_, vec) ->
            sqrt(vec.values.sumOf { it * it })
        }
    }

    /**
     * 사용자 별 게시글에 대한 가중치 계산
     */
    @MeasureExecutionTime
    fun calculateWeight(): List<UserPostScore> {
        return weightQueryRepository.findAllWeights()
    }

    /**
     * 주어진 weights 데이터를 기반으로 사용자 간 코사인 유사도를 효율적으로 계산하는 함수.
     * 역색인 방식을 사용하여 실제 공통 게시글에 대해서만 내적을 누적합니다.
     */
    @MeasureExecutionTime
    fun computeUserSimilaritiesEfficient(weights: List<UserPostScore>): List<UserSimilarity> {
        // 역색인 구축: postId -> List of (userId, weight)
        val invertedIndex: MutableMap<Long, MutableList<Pair<Long, Double>>> = mutableMapOf()
        for (record in weights) {
            invertedIndex.computeIfAbsent(record.postId) { mutableListOf() }
                .add(record.userId to record.weight)
        }

        // 사용자 쌍별로 내적을 누적할 지도 (정렬된 순서로 키를 관리)
        val dotProducts = mutableMapOf<Pair<Long, Long>, Double>()

        // 각 게시글(postId)별로, 해당 게시글에 참여한 사용자들의 쌍에 대해 내적 기여를 누적
        for ((postId, userList) in invertedIndex) {
            if (userList.size <= 1) continue  // 이 게시글은 한 명만 참여하면 계산할 필요 없음.
            for (i in 0 until userList.size) {
                for (j in i + 1 until userList.size) {
                    val (userA, weightA) = userList[i]
                    val (userB, weightB) = userList[j]
                    // 항상 (min(userA, userB), max(userA, userB)) 형태로 키 생성
                    val key = if (userA < userB) Pair(userA, userB) else Pair(userB, userA)
                    dotProducts[key] = dotProducts.getOrDefault(key, 0.0) + weightA * weightB
                }
            }
        }

        // 사용자 쌍별 코사인 유사도 계산
        val similarities = mutableListOf<UserSimilarity>()
        for ((pair, dot) in dotProducts) {
            val (u1, u2) = pair
            val norm1 = userNorms[u1] ?: 0.0
            val norm2 = userNorms[u2] ?: 0.0
            val sim = if (norm1 == 0.0 || norm2 == 0.0) 0.0 else dot / (norm1 * norm2)
            similarities.add(UserSimilarity(u1, u2, sim))
        }

        return similarities
    }

    /**
     * 추천 게시글 생성
     */
    @MeasureExecutionTime
    fun generateRecommendations(
        weights: List<UserPostScore>, similarities: List<UserSimilarity>
    ): Map<Long, List<Recommendation>> {
        // 사용자 간 유사도 매핑 (양방향)
        val similarityMap: Map<Long, List<Pair<Long, Double>>> = similarities
            .flatMap { sim ->
                listOf(
                    sim.userId1 to (sim.userId2 to sim.similarity),
                    sim.userId2 to (sim.userId1 to sim.similarity)
                )
            }
            .groupBy({ it.first }, { it.second })

        // 추천 점수 계산
        val recommendations = mutableMapOf<Long, MutableMap<Long, Double>>()  // userId -> (postId -> predictedScore)
        val allUserIds = userVectors.keys

        for (userId in allUserIds) {
            val seenPosts = userPosts[userId] ?: emptySet()
            val similarUsers = similarityMap[userId] ?: continue

            for ((otherUserId, sim) in similarUsers) {
                val otherUserVector = userVectors[otherUserId] ?: continue
                for ((postId, weight) in otherUserVector) {
                    if (postId in seenPosts) continue  // 이미 본 게시글은 제외
                    recommendations.getOrPut(userId) { mutableMapOf() }
                        .merge(postId, sim * weight) { old, new -> old + new }
                }
            }
        }

        // 각 사용자별로 추천 게시글 내림차순 정렬 후 목록 생성
        return recommendations.mapValues { (userId, recMap) ->
            recMap.entries.sortedByDescending { it.value }
                .map { (postId, predictedScore) ->
                    Recommendation(userId = userId, postId = postId, predictedScore = predictedScore)
                }
        }
    }
}
