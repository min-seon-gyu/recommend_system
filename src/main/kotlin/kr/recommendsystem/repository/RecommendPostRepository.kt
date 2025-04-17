package kr.recommendsystem.repository

import kr.recommendsystem.domain.RecommendPost
import kr.recommendsystem.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface RecommendPostRepository : JpaRepository<RecommendPost, Long> {
    @Query
    fun findByUser(user: User): List<RecommendPost>
}

