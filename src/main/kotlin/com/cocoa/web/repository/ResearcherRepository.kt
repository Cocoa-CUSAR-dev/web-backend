package com.cocoa.web.repository

import com.cocoa.generated.auth.Tables.ROLE
import com.cocoa.generated.auth.Tables.USER_ROLE
import com.cocoa.generated.research.Tables.RESEARCHER
import com.cocoa.web.base.BaseRepository
import com.cocoa.web.model.Researcher
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class ResearcherRepository(
    dsl: DSLContext,
) : BaseRepository(dsl) {

    fun insertResearcher(
        userId: UUID,
        researcherInfo: Researcher.Input.Create,
    ) {
        dsl.transaction { config ->
            val transactionDsl = DSL.using(config)

            transactionDsl.insertInto(RESEARCHER)
                .set(RESEARCHER.USER_ID, userId)
                .set(RESEARCHER.FIRST_NAME, researcherInfo.firstName)
                .set(RESEARCHER.LAST_NAME, researcherInfo.lastName)
                .set(RESEARCHER.ORGANIZATION, researcherInfo.organization)
                .execute()

            transactionDsl
                .insertInto(USER_ROLE, USER_ROLE.USER_ID, USER_ROLE.ROLE_ID)
                .select(
                    transactionDsl
                        .select(DSL.value(userId), ROLE.ROLE_ID)
                        .from(ROLE)
                        .where(ROLE.ROLE_NAME.eq("researcher"))
                )
                .execute()
        }
    }

    fun findByUserId(userId: UUID): Researcher.Entity? {
        val record = dsl.selectFrom(RESEARCHER)
            .where(RESEARCHER.USER_ID.eq(userId))
            .fetchOne()

        return record?.toEntity()
    }

    fun fetchResearchers(filter: Researcher.Query.Filter): List<Researcher.Entity> {
        val conditions = mutableListOf<Condition>()

        filter.firstName?.let { conditions.add(RESEARCHER.FIRST_NAME.eq(it)) }
        filter.lastName?.let { conditions.add(RESEARCHER.LAST_NAME.eq(it)) }
        filter.organization?.let { conditions.add(RESEARCHER.ORGANIZATION.eq(it)) }

        val query = dsl.selectFrom(RESEARCHER)
        val records = if (conditions.isNotEmpty()) {
            query.where(conditions).fetch()
        } else {
            query.fetch()
        }

        return records.map { it.toEntity() }
    }

    fun patchResearcher(userId: UUID, researcherUpdate: Researcher.Input.Update): Int {
        val updates = mutableMapOf<Field<*>, Any>()

        researcherUpdate.firstName?.let { updates[RESEARCHER.FIRST_NAME] = it }
        researcherUpdate.lastName?.let { updates[RESEARCHER.LAST_NAME] = it }
        researcherUpdate.organization?.let { updates[RESEARCHER.ORGANIZATION] = it }

        if (updates.isEmpty()) throw IllegalArgumentException("No fields to update")

        return dsl.update(RESEARCHER)
            .set(updates)
            .where(RESEARCHER.USER_ID.eq(userId))
            .execute()
    }

    fun deleteResearcher(userId: UUID): Int {
        return dsl.transactionResult { config ->
            val transactionDsl = DSL.using(config)

            transactionDsl.delete(USER_ROLE)
                .where(USER_ROLE.USER_ID.eq(userId))
                .execute()

            return@transactionResult transactionDsl
                .delete(RESEARCHER)
                .where(RESEARCHER.USER_ID.eq(userId))
                .execute()
        }
    }

    private fun Record.toEntity(): Researcher.Entity {
        return Researcher.Entity(
            userId = this.get(RESEARCHER.USER_ID),
            firstName = this.get(RESEARCHER.FIRST_NAME),
            lastName = this.get(RESEARCHER.LAST_NAME),
            organization = this.get(RESEARCHER.ORGANIZATION),
        )
    }

}