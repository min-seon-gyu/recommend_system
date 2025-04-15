package kr.recommendsystem.scheduler

import kr.recommendsystem.config.MeasureExecutionTime
import kr.recommendsystem.repository.WeightScoreQueryRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import kotlin.math.sqrt

@Component
class RecommendScheduler(
    private val weightQueryRepository: WeightScoreQueryRepository
) {

    /**
     * 추천 게시글 저장을 위한 스케줄러
     * 매일 03:00에 실행
     */
    @Scheduled(cron = "0 0 3 * * *")
    @MeasureExecutionTime
    fun calculateSimilarity(){
        val weights = calculateWeight()

        calculateUserCosineSimilarity(weights)
    }

    /**
     * 사용자 별 게시글에 대한 가중치 계산
     */
    private fun calculateWeight(): List<UserPostScore> {
        val weights = weightQueryRepository.findAllWeights()

        weights.printPretty()

        return weights
    }

    /**
     * 사용자 간 코사인 유사도 계산
     */
    private fun calculateUserCosineSimilarity(weights: List<UserPostScore>): List<UserSimilarity> {
        // 사용자별 벡터 구성: 각 사용자의 게시글별 weight 값을 Map으로 만듦.
        // 예를 들어, userId 1 -> {postId1: weight1, postId2: weight2, ... }
        val userVectors: Map<Long, Map<Long, Double>> = weights
            .groupBy { it.userId }
            .mapValues { entry ->
                entry.value.associate { it.postId to it.weight.toDouble() }
            }

        // 각 사용자 간 유사도 계산 (중복 없이 모든 쌍을 계산)
        val userIds = userVectors.keys.toList()
        val similarities = mutableListOf<UserSimilarity>()

        for (i in userIds.indices) {
            for (j in i + 1 until userIds.size) {
                val userId1 = userIds[i]
                val userId2 = userIds[j]
                val sim = cosineSimilarity(userVectors[userId1]!!, userVectors[userId2]!!)
                similarities.add(UserSimilarity(userId1, userId2, sim))
            }
        }

        similarities.printPretty()

        return similarities
    }

    fun cosineSimilarity(
        vec1: Map<Long, Double>,
        vec2: Map<Long, Double>
    ): Double {
        // 두 벡터의 공통 요소(게시글)를 찾음
        val commonPosts = vec1.keys.intersect(vec2.keys)
        // 공통 게시글에 대한 내적 계산
        val dotProduct = commonPosts.sumOf { postId -> vec1[postId]!! * vec2[postId]!! }
        // 각 벡터의 유클리드 노름 계산
        val norm1 = sqrt(vec1.values.sumOf { it * it })
        val norm2 = sqrt(vec2.values.sumOf { it * it })
        return if (norm1 == 0.0 || norm2 == 0.0) 0.0 else dotProduct / (norm1 * norm2)
    }
}
