package com.cocoa.web.base

import org.jooq.DSLContext

abstract class BaseRepository(
    protected val dsl: DSLContext
) {

//    fun fetchCurrentRunningNumber(tableCode: String): Int {
//        val runningNumberRecord = dsl.select(
//            RUNNING_NUMBER.CURRENT_NUMBER
//        )
//            .from(RUNNING_NUMBER)
//            .where(RUNNING_NUMBER.TABLE_CODE.eq(tableCode))
//            .fetchOne()
//
//        return runningNumberRecord?.get(RUNNING_NUMBER.CURRENT_NUMBER)
//            ?: throw InvalidTableCodeException()
//    }
//
//    fun <T> updateRunningNumber(
//        tableCode: String,
//        operation: (DSLContext, Int) -> T
//    ): T {
//        return dsl.transactionResult { config ->
//            val transactionDsl = DSL.using(config)
//            val lockId = tableCode.hashCode().toLong()
//
//            transactionDsl.execute("SELECT pg_advisory_xact_lock(?)", lockId)
//
//            val nowTime = Instant.now().atOffset(ZoneOffset.UTC).toLocalDateTime()
//            val newRunningNumber = transactionDsl.update(RUNNING_NUMBER)
//                .set(RUNNING_NUMBER.CURRENT_NUMBER, RUNNING_NUMBER.CURRENT_NUMBER.plus(1))
//                .set(RUNNING_NUMBER.UPDATED_AT, nowTime)
//                .where(RUNNING_NUMBER.TABLE_CODE.eq(tableCode))
//                .returning(RUNNING_NUMBER.CURRENT_NUMBER)
//                .fetchOne()
//                ?.get(RUNNING_NUMBER.CURRENT_NUMBER)
//                ?: throw InvalidTableCodeException()
//
//            operation(transactionDsl, newRunningNumber)
//        }
//    }
}