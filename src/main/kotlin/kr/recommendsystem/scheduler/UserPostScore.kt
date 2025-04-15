package kr.recommendsystem.scheduler

data class UserPostScore(
    val userId: Long,
    val postId: Long,
    val weight: Long
)

fun List<UserPostScore>.printPretty() {
    /* 헤더 출력 */
    println("---------가중치 계산 결과---------")
    println("%-10s %-10s %-12s".format("user_id", "post_id", "weight"))

    /* 본문 출력 */
    forEach {
        println(
            "%-10d %-10d %-12d".format(
                it.userId,
                it.postId,
                it.weight
            )
        )
    }
    println("------------------------------")
    println()
}
