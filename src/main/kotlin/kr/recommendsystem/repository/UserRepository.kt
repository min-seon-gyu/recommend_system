package kr.recommendsystem.repository

import kr.recommendsystem.domain.User
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Long>
