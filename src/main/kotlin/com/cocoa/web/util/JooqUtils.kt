package com.cocoa.web.util

import com.cocoa.web.model.Analytics
import org.jooq.Condition
import org.jooq.Field
import org.jooq.impl.DSL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit

fun Condition.withDateRange(
    filter: Analytics.Query.TimeRangeFilter,
    field: Field<LocalDateTime>
): Condition {
    val start = filter.from.atDay(1).atStartOfDay()
    val end = filter.to.plusMonths(1).atDay(1).atStartOfDay()

    return this.and(field.ge(start)).and(field.lt(end))
}

fun generateMonthRange(from: YearMonth, to: YearMonth): List<YearMonth> {
    if (from.isAfter(to)) return emptyList()

    val months = mutableListOf<YearMonth>()
    var current = from
    while (!current.isAfter(to)) {
        months.add(current)
        current = current.plusMonths(1)
    }
    return months
}

fun Field<LocalDateTime>.dateTrunc(precision: String): Field<LocalDate> {
    return DSL.field(
        "date_trunc({0}, {1})::date",
        LocalDate::class.java,
        DSL.inline(precision),
        this
    )
}