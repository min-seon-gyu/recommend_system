package kr.recommendsystem.repository

import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Repository
import java.time.Duration

@Repository
class RecommendPostRepository(
    userPostRedisTemplate: RedisTemplate<Long, List<Long>>
) {
    private val valueOps = userPostRedisTemplate.opsForValue()

    suspend fun get(userId: Long): List<Long>? {
        return valueOps.get(userId)
    }

    suspend fun set(userId: Long, posts: List<Long>) {
        valueOps.set(userId, posts, Duration.ofMinutes(5))
    }
}
