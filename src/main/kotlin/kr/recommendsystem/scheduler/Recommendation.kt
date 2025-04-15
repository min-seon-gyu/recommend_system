package kr.recommendsystem.scheduler

class Recommendation(
    val userId: Long,
    val postId: Long,
    val predictedScore: Double
)

fun List<Recommendation>.printPretty() {
    /* 헤더 출력 */
    println("---------추천 게시글 계산 결과---------")
    println("%-10s %-10s %-12s".format("user_id", "post_id", "predictedScore"))

    /* 본문 출력 */
    forEach {
        println(
            "%-10d %-10d %-12f".format(
                it.userId,
                it.postId,
                it.predictedScore
            )
        )
    }
    println("----------------------------------")
    println()
}