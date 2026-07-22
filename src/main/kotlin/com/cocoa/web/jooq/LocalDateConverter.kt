package com.cocoa.web.jooq

import org.jooq.impl.AbstractConverter
import java.time.LocalDate
import java.sql.Date

class LocalDateConverter : AbstractConverter<Date, LocalDate>(Date::class.java, LocalDate::class.java) {
    override fun from(o: Date?): LocalDate? {
        if (o == null) return null
        return o.toLocalDate()
    }

    override fun to(o: LocalDate?): Date? {
        if (o == null) return null
        return Date.valueOf(o)
    }
}