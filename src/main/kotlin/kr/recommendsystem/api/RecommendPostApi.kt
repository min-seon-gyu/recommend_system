package kr.recommendsystem.api

import kotlinx.coroutines.runBlocking
import kr.recommendsystem.service.RecommendPostService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/recommend")
class RecommendPostApi(
    private val recommendPostService: RecommendPostService
) {

    @GetMapping("/{userId}")
    suspend fun get(@PathVariable userId: Long) {
        recommendPostService.getRecommendPosts(userId)
    }
}
