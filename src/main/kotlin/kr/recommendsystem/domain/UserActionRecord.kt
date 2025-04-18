package kr.recommendsystem.domain

import jakarta.persistence.*
import java.time.OffsetDateTime

/**
 * 사용자가 어떤 게시글에 좋아요 여부와 조회 횟수를 기록합니다. 한 사용자-게시글 쌍 당 최대 한 행만 존재합니다 (좋아요는 한 번만 가능).
 */
@Entity
/** post_id, user_id 조합이 유일해야 하므로 unique constraint를 설정합니다. **/
@Table(
    uniqueConstraints = [
        UniqueConstraint(name = "UniquePostIdAndUserId", columnNames = ["post_id", "user_id"])
    ])
class UserActionRecord(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    /** 사용자 **/
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    /** 게시글 **/
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    val post: Post,

    /** 조회 횟수 **/
    @Column(nullable = false)
    var viewCount: Long = 0L,

    /** 좋아요 여부 **/
    @Column(nullable = false)
    var favorite: Boolean = false,

    /** 마지막 활동 시간 **/
    @Column(nullable = false)
    val timestamp: OffsetDateTime = OffsetDateTime.now()
)
