package kr.recommendsystem.service

/**
 * 두 사용자 간의 유사도 정보를 담는 데이터 클래스
 *
 * @property baseUserId    유사도를 기준으로 삼는 사용자 ID
 * @property targetUserId  비교 대상 사용자 ID
 * @property similarity    코사인 유사도 값 (0.0 ~ 1.0)
 */
data class UserSimilarity(
    val baseUserId: Long,
    val targetUserId: Long,
    val similarity: Double
)
