package kr.recommendsystem.config

import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.GenericToStringSerializer

@Configuration
@EnableCaching
class RedisConfig(
    private val redisHost: String = "localhost",
    private val redisPort: Int = 6379
) {
    @Bean
    fun redisConnectionFactory(): RedisConnectionFactory {
        return LettuceConnectionFactory(redisHost, redisPort)
    }

    @Bean
    fun userPostRedisTemplate(redisConnectionFactory: RedisConnectionFactory): RedisTemplate<Long, List<Long>> {
        return RedisTemplate<Long, List<Long>>().apply {
            connectionFactory = redisConnectionFactory
            keySerializer = GenericToStringSerializer(Long::class.java) // Long → String 직렬화
            valueSerializer = GenericJackson2JsonRedisSerializer()      // List<Long> → JSON
        }
    }
}
