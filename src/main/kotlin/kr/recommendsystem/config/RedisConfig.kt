package kr.recommendsystem.config

import org.springframework.beans.factory.annotation.Value
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
    @Value("\${redis.host}")
    private val host: String,
    @Value("\${redis.port}")
    private val port: Int
) {
    @Bean
    fun redisConnectionFactory(): RedisConnectionFactory {
        return LettuceConnectionFactory(host, port)
    }

    @Bean
    fun userPostRedisTemplate(redisConnectionFactory: RedisConnectionFactory): RedisTemplate<Long, List<Long>> {
        return RedisTemplate<Long, List<Long>>().apply {
            connectionFactory = redisConnectionFactory
            keySerializer = GenericToStringSerializer(Long::class.java)
            valueSerializer = GenericJackson2JsonRedisSerializer()
        }
    }
}
