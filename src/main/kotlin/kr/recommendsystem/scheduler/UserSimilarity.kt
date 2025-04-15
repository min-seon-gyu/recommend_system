package kr.recommendsystem.scheduler

data class UserSimilarity(
    val userId1: Long,
    val userId2: Long,
    val similarity: Double
)

fun List<UserSimilarity>.printPretty() {
    /* 헤더 출력 */
    println("-----------유사도 계산 결과-----------")
    println("%-10s %-10s %-12s".format("user_id1", "user_id2", "similarity"))

    /* 본문 출력 */
    forEach {
        println(
            "%-10d %-10d %-12f".format(
                it.userId1,
                it.userId2,
                it.similarity
            )
        )
    }
    println("----------------------------------")
    println()
}
