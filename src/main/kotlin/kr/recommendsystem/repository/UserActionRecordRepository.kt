package kr.recommendsystem.repository

import com.linecorp.kotlinjdsl.dsl.jpql.jpql
import com.linecorp.kotlinjdsl.render.RenderContext
import com.linecorp.kotlinjdsl.support.spring.data.jpa.extension.createQuery
import jakarta.persistence.EntityManager
import kr.recommendsystem.domain.Post
import kr.recommendsystem.domain.User
import kr.recommendsystem.domain.UserActionRecord
import kr.recommendsystem.scheduler.UserPostScore
import org.springframework.data.jpa.repository.JpaRepository

interface UserActionRecordRepository : JpaRepository<UserActionRecord, Long>, UserPostScoreRepository

interface UserPostScoreRepository {
    fun findAllWeights(): List<UserPostScore>
}

class UserPostScoreRepositoryImpl(
    private val entityManager: EntityManager,
    private val jpqlRenderContext: RenderContext
) : UserPostScoreRepository {
    override fun findAllWeights(): List<UserPostScore> {
        val query = jpql {
            val viewScore =
                caseWhen(path(UserActionRecord::viewCount).gt(5L))
                    .then(5L)
                    .`else`(path(UserActionRecord::viewCount))

            val favoriteScore =
                caseWhen(path(UserActionRecord::favorite).eq(true))
                    .then(10L)
                    .`else`(0L)

            val weight = sum(favoriteScore.plus(viewScore))

            selectNew<UserPostScore>(
                path(UserActionRecord::user)(User::id).alias(expression("userId")),
                path(UserActionRecord::post)(Post::id).alias(expression("postId")),
                weight.alias(expression("weight"))
            )
                .from(
                    entity(UserActionRecord::class)
                )
                .groupBy(
                    path(UserActionRecord::post)(Post::id),
                    path(UserActionRecord::user)(User::id)
                )
        }

        return entityManager.createQuery(query, jpqlRenderContext).resultList
    }
}
