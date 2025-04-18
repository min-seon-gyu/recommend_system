package kr.recommendsystem.domain

import jakarta.persistence.*

@Entity
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    /** 이름 **/
    @Column(unique = true, nullable = false)
    val username: String
)
