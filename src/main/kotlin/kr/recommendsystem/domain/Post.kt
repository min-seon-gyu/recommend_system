package kr.recommendsystem.domain

import jakarta.persistence.*

@Entity
class Post(
    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    var id: Long? = null,

    /** 제목 **/
    @Column(nullable = false)
    val title: String,

    /** 내용 **/
    @Column(nullable = false)
    val content: String,

    /** 작성자 **/
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User
)
