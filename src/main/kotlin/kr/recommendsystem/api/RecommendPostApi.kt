package kr.recommendsystem.api

import kr.recommendsystem.domain.RecommendPost
import kr.recommendsystem.repository.RecommendPostRepository
import kr.recommendsystem.repository.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/recommend")
class RecommendPostApi(
    private val userRepository: UserRepository,
    private val recommendPostRepository: RecommendPostRepository
) {

    @GetMapping("/{userId}")
    fun get(@PathVariable userId: Long): ResponseEntity<List<RecommendPost>> {
        val user = userRepository.findById(userId).get()
        val recommendPosts = recommendPostRepository.findByUser(user = user)

        return ResponseEntity.status(HttpStatus.OK).body(recommendPosts)
    }
}
