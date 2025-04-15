package kr.recommendsystem.api

import kr.recommendsystem.scheduler.RecommendScheduler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/scheduler")
class SchedulerApi(
    private val recommendScheduler: RecommendScheduler
) {

    @GetMapping
    fun runScheduler() {
        recommendScheduler.calculateSimilarity()
    }
}
