package kr.recommendsystem.api

import kr.recommendsystem.repository.PostRepository
import kr.recommendsystem.repository.UserActionRecordRepository
import kr.recommendsystem.repository.UserRepository
import kr.recommendsystem.scheduler.RecommendScheduler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/scheduler")
class SchedulerApi(
    private val recommendScheduler: RecommendScheduler,
    private val userRepository: UserRepository,
    private val postRepository: PostRepository,
    private val userActionRecordRepository: UserActionRecordRepository,
) {

    @GetMapping
    fun runScheduler() {
        recommendScheduler.calculateSimilarity()
    }
}
