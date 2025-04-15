package kr.recommendsystem.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id

@Entity
class Post(
    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false)
    val title: String,

    @Column(nullable = false)
    val content: String
)
