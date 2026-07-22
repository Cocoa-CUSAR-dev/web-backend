package com.cocoa.web.jooq

import java.sql.Time
import java.time.LocalTime
import org.jooq.impl.AbstractConverter

class LocalTimeConverter : AbstractConverter<Time, LocalTime>(Time::class.java, LocalTime::class.java) {
    override fun from(o: Time?): LocalTime? {
        if (o == null) return null
        return o.toLocalTime()
    }

    override fun to(o: LocalTime?): Time? {
        if (o == null) return null
        return Time.valueOf(o)
    }
}