package kr.recommendsystem.repository

import kr.recommendsystem.domain.Post
import org.springframework.data.jpa.repository.JpaRepository

interface PostRepository : JpaRepository<Post, Long>