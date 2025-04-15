package kr.recommendsystem

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class RecommendSystemApplication

fun main(args: Array<String>) {
    runApplication<RecommendSystemApplication>(*args)
}
