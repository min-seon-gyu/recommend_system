package kr.recommendsystem.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id

@Entity
class User(
    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    var id: Long? = null,

    /** 이름 **/
    @Column(unique = true, nullable = false)
    val username: String
)
