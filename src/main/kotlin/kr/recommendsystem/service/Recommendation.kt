package kr.recommendsystem.service

/**
 * 추천 게시글 계산 결과
 *
 * @property userId 사용자 ID
 * @property postId 게시글 ID
 * @property score 추천 점수
 */
data class Recommendation(
    val userId: Long,
    val postId: Long,
    val score: Double
)
