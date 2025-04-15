package kr.recommendsystem.repository

import kr.recommendsystem.domain.RecommendPost
import kr.recommendsystem.domain.User
import org.springframework.data.jpa.repository.JpaRepository

interface RecommendPostRepository : JpaRepository<RecommendPost, Long> {
    fun findByUser(user: User): List<RecommendPost>
}

