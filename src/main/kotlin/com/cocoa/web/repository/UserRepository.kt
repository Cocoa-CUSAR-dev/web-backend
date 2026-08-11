package com.cocoa.web.repository

import com.cocoa.generated.auth.Tables.PERMISSION
import com.cocoa.generated.auth.Tables.ROLE
import com.cocoa.generated.auth.Tables.ROLE_PERMISSION
import com.cocoa.generated.auth.Tables.USER_ACCOUNT
import com.cocoa.generated.auth.Tables.USER_ROLE
import com.cocoa.generated.research.Tables.RESEARCHER
import com.cocoa.web.base.BaseRepository
import com.cocoa.web.exception.EntityNotFoundException
import com.cocoa.web.model.Analytics
import com.cocoa.web.model.User
import com.cocoa.web.util.dateTrunc
import com.cocoa.web.util.withDateRange
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.jooq.impl.DSL.arrayAggDistinct
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.UUID

@Repository
class UserRepository(
    dsl: DSLContext,
    private val encoder: PasswordEncoder,
) : BaseRepository(dsl) {
    data class MonthlyRow(val month: LocalDate, val value: Long)

    fun fetchMonthlyDelta(filter: Analytics.Query.UserFilter): List<MonthlyRow> {
        val monthBucket = USER_ACCOUNT.CREATED_AT.dateTrunc("month")
        var conditions = DSL.noCondition().withDateRange(filter, USER_ACCOUNT.CREATED_AT)

        filter.roleName?.let { conditions = conditions.and(ROLE.ROLE_NAME.eq(it)) }

        return dsl.select(monthBucket.`as`("month"), DSL.count().`as`("value"))
            .from(USER_ACCOUNT)
            .join(USER_ROLE).on(USER_ROLE.USER_ID.eq(USER_ACCOUNT.USER_ID))
            .join(ROLE).on(ROLE.ROLE_ID.eq(USER_ROLE.ROLE_ID))
            .where(conditions)
            .groupBy(monthBucket)
            .orderBy(monthBucket.asc())
            .fetch { r ->
                MonthlyRow(
                    month = r.get("month", LocalDate::class.java),
                    value = r.get("value", Long::class.java),
                )
            }
    }

    fun fetchTotalUserCount(filter: Analytics.Query.UserFilter): Long {
        var conditions = DSL.noCondition().withDateRange(filter, USER_ACCOUNT.CREATED_AT)

        filter.roleName?.let { conditions = conditions.and(ROLE.ROLE_NAME.eq(it)) }

        return dsl.select(DSL.count())
            .from(USER_ACCOUNT)
            .join(USER_ROLE).on(USER_ROLE.USER_ID.eq(USER_ACCOUNT.USER_ID))
            .join(ROLE).on(ROLE.ROLE_ID.eq(USER_ROLE.ROLE_ID))
            .where(conditions)
            .fetchOneInto(Long::class.java) ?: 0L
    }

    fun insertUser(
        userInfo: User.Input.Create,
        requiresPasswordReset: Boolean = false,
        roleName: String? = null,
    ): UUID {
        return dsl.transactionResult { config ->
            val transactionDsl = DSL.using(config)
            val userId =
                transactionDsl.insertInto(USER_ACCOUNT)
                    .set(USER_ACCOUNT.USERNAME, userInfo.username)
                    .set(USER_ACCOUNT.PASSWORD_HASH, encoder.encode(userInfo.password))
                    .set(USER_ACCOUNT.IS_PASSWORD_RESET, false)
                    .set(USER_ACCOUNT.IS_REQUIRES_PASSWORD_RESET, requiresPasswordReset)
                    .returning(USER_ACCOUNT.USER_ID)
                    .fetchOne()?.get(USER_ACCOUNT.USER_ID)
                    ?: throw IllegalStateException("User creation failed")

            roleName?.let { name ->
                val roleId =
                    transactionDsl.select(ROLE.ROLE_ID)
                        .from(ROLE)
                        .where(ROLE.ROLE_NAME.eq(name))
                        .fetchOne()?.get(ROLE.ROLE_ID)
                        ?: throw EntityNotFoundException("Role '$name' not found in system")

                transactionDsl.insertInto(USER_ROLE)
                    .set(USER_ROLE.USER_ID, userId)
                    .set(USER_ROLE.ROLE_ID, roleId)
                    .execute()
            }

            return@transactionResult userId
        }
    }

    fun fetchUser(username: String): User.Entity? {
        val record =
            dsl.select(
                USER_ACCOUNT.USER_ID,
                USER_ACCOUNT.USERNAME,
                USER_ACCOUNT.PASSWORD_HASH,
                USER_ACCOUNT.IS_PASSWORD_RESET,
                USER_ACCOUNT.CREATED_AT,
                USER_ACCOUNT.UPDATED_AT,
                arrayAggDistinct(ROLE.ROLE_NAME).`as`("roles"),
                arrayAggDistinct(PERMISSION.PERMISSION_KEY).`as`("permissions"),
            )
                .from(USER_ACCOUNT)
                .leftJoin(USER_ROLE).on(USER_ACCOUNT.USER_ID.eq(USER_ROLE.USER_ID))
                .leftJoin(ROLE).on(USER_ROLE.ROLE_ID.eq(ROLE.ROLE_ID))
                .leftJoin(ROLE_PERMISSION).on(ROLE.ROLE_ID.eq(ROLE_PERMISSION.ROLE_ID))
                .leftJoin(PERMISSION).on(ROLE_PERMISSION.PERMISSION_ID.eq(PERMISSION.PERMISSION_ID))
                .where(USER_ACCOUNT.USERNAME.eq(username))
                .groupBy(USER_ACCOUNT.USER_ID)
                .fetchOne()

        return record?.let {
            User.Entity(
                userId = it[USER_ACCOUNT.USER_ID],
                username = it[USER_ACCOUNT.USERNAME],
                passwordHash = it[USER_ACCOUNT.PASSWORD_HASH],
                isPasswordReset = it[USER_ACCOUNT.IS_PASSWORD_RESET],
                roles = (it["roles"] as? Array<*>)?.mapNotNull { r -> r?.toString() } ?: emptyList(),
                permissions = (it["permissions"] as? Array<*>)?.mapNotNull { p -> p?.toString() } ?: emptyList(),
                createdAt = it[USER_ACCOUNT.CREATED_AT],
                updatedAt = it[USER_ACCOUNT.UPDATED_AT],
            )
        }
    }

    fun fetchUserDetail(userId: UUID): User.Detail? {
        val records =
            dsl.select(
                USER_ACCOUNT.USER_ID,
                USER_ACCOUNT.USERNAME,
                USER_ACCOUNT.IS_PASSWORD_RESET,
                USER_ACCOUNT.IS_REQUIRES_PASSWORD_RESET,
                ROLE.ROLE_NAME,
                RESEARCHER.FIRST_NAME,
                RESEARCHER.LAST_NAME,
                RESEARCHER.ORGANIZATION,
            )
                .from(USER_ACCOUNT)
                .leftJoin(USER_ROLE).on(USER_ROLE.USER_ID.eq(USER_ACCOUNT.USER_ID))
                .leftJoin(ROLE).on(ROLE.ROLE_ID.eq(USER_ROLE.ROLE_ID))
                .leftJoin(RESEARCHER).on(RESEARCHER.USER_ID.eq(USER_ACCOUNT.USER_ID))
                .where(USER_ACCOUNT.USER_ID.eq(userId))
                .fetch()

        if (records.isEmpty()) return null

        val first = records.first()
        return User.Detail(
            userId = first.get(USER_ACCOUNT.USER_ID),
            email = first.get(USER_ACCOUNT.USERNAME),
            firstName = first.get(RESEARCHER.FIRST_NAME),
            lastName = first.get(RESEARCHER.LAST_NAME),
            organization = first.get(RESEARCHER.ORGANIZATION),
            isPasswordReset = first.get(USER_ACCOUNT.IS_PASSWORD_RESET),
            isRequiresPasswordReset = first.get(USER_ACCOUNT.IS_REQUIRES_PASSWORD_RESET),
            roles =
                records
                    .mapNotNull { it.get(ROLE.ROLE_NAME) }
                    .distinct(),
        )
    }

    fun updatePassword(
        userId: UUID,
        newPassword: String,
    ) {
        dsl.update(USER_ACCOUNT)
            .set(USER_ACCOUNT.PASSWORD_HASH, encoder.encode(newPassword))
            .set(USER_ACCOUNT.IS_PASSWORD_RESET, true)
            .set(USER_ACCOUNT.IS_REQUIRES_PASSWORD_RESET, false)
            .where(USER_ACCOUNT.USER_ID.eq(userId))
            .execute()
    }

    private fun Record.toEntity(): User.Entity {
        fun handleArray(fieldName: String): List<String> {
            val obj = this.get(fieldName)
            return when (obj) {
                is Array<*> -> obj.mapNotNull { it?.toString() }
                is java.sql.Array -> (obj.array as Array<*>).mapNotNull { it?.toString() }
                else -> emptyList()
            }
        }

        return User.Entity(
            userId = this.get(USER_ACCOUNT.USER_ID)!!,
            username = this.get(USER_ACCOUNT.USERNAME)!!,
            passwordHash = this.get(USER_ACCOUNT.PASSWORD_HASH)!!,
            isPasswordReset = this.get(USER_ACCOUNT.IS_PASSWORD_RESET)!!,
            roles = handleArray("roles"),
            permissions = handleArray("permissions"),
            createdAt = this.get(USER_ACCOUNT.CREATED_AT)!!,
            updatedAt = this.get(USER_ACCOUNT.UPDATED_AT)!!,
        )
    }
}
