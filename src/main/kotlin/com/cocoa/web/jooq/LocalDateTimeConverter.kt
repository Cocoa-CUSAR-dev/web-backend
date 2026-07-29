package com.cocoa.web.jooq

import org.jooq.impl.AbstractConverter
import java.sql.Timestamp
import java.time.LocalDateTime

class LocalDateTimeConverter : AbstractConverter<Timestamp, LocalDateTime>(Timestamp::class.java, LocalDateTime::class.java) {
    override fun from(o: Timestamp?): LocalDateTime? {
        if (o == null) return null
        return o.toLocalDateTime()
    }

    override fun to(o: LocalDateTime?): Timestamp? {
        if (o == null) return null
        return Timestamp.valueOf(o)
    }
}
