package kr.recommendsystem.domain

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(
    uniqueConstraints = [
        UniqueConstraint(name = "UniquePostIdAndUserId", columnNames = ["post_id", "user_id"])
    ],
    indexes = [
        Index(name = "idx_user_id", columnList = "user_id")
    ])
class RecommendPost(
    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    var id: Long? = null,

    /** 추천 게시글 **/
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    val post: Post,

    /** 추천 게시글 사용자 **/
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    /** 추천 게시글 점수 **/
    @Column(nullable = false)
    val score: Double = 0.0,

    /** 추천 게시글 생성 시간 **/
    @Column(nullable = false)
    val timestamp: OffsetDateTime = OffsetDateTime.now()
)
