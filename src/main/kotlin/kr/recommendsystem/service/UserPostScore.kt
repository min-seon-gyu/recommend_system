package kr.recommendsystem.service

/**
 * 사용자 게시글 가중치 계산 결과
 *
 * @property userId 사용자 ID
 * @property postId 게시글 ID
 * @property weight 가중치 점수
 */
data class UserPostScore(
    val userId: Long,
    val postId: Long,
    val weight: Double
)
