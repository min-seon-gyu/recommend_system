package kr.recommendsystem.repository

import com.linecorp.kotlinjdsl.dsl.jpql.jpql
import com.linecorp.kotlinjdsl.render.RenderContext
import com.linecorp.kotlinjdsl.support.spring.data.jpa.extension.createQuery
import jakarta.persistence.EntityManager
import kr.recommendsystem.domain.Post
import kr.recommendsystem.domain.User
import kr.recommendsystem.domain.UserPostAction
import kr.recommendsystem.service.UserPostScore
import org.springframework.data.jpa.repository.JpaRepository

interface UserPostActionRepository : JpaRepository<UserPostAction, Long>, UserPostWeightRepository

interface UserPostWeightRepository {
    fun findAllWeights(): List<UserPostScore>
}

class UserPostWeightRepositoryImpl(
    private val entityManager: EntityManager,
    private val jpqlRenderContext: RenderContext
) : UserPostWeightRepository {
    override fun findAllWeights(): List<UserPostScore> {
        val query = jpql {
            val viewScore =
                caseWhen(path(UserPostAction::viewCount).gt(5L))
                    .then(5L)
                    .`else`(path(UserPostAction::viewCount))

            val favoriteScore =
                caseWhen(path(UserPostAction::favorite).eq(true))
                    .then(10L)
                    .`else`(0L)

            selectNew<UserPostScore>(
                path(UserPostAction::user)(User::id),
                path(UserPostAction::post)(Post::id),
                sum(favoriteScore.plus(viewScore))
            )
                .from(
                    entity(UserPostAction::class)
                )
                .groupBy(
                    path(UserPostAction::post)(Post::id),
                    path(UserPostAction::user)(User::id)
                )
        }

        return entityManager.createQuery(query, jpqlRenderContext).resultList
    }
}
