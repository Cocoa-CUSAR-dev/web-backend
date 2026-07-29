package com.cocoa.web.jooq

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.jooq.Binding
import org.jooq.BindingGetResultSetContext
import org.jooq.BindingGetSQLInputContext
import org.jooq.BindingGetStatementContext
import org.jooq.BindingRegisterContext
import org.jooq.BindingSQLContext
import org.jooq.BindingSetSQLOutputContext
import org.jooq.BindingSetStatementContext
import org.jooq.Converter
import org.jooq.JSONB
import org.jooq.conf.ParamType
import java.sql.SQLException
import java.sql.SQLFeatureNotSupportedException
import java.sql.Types

class JsonbNodeBinding : Binding<JSONB, JsonNode> {
    companion object {
        private val mapper = ObjectMapper().registerKotlinModule()
        val converter = JsonbNodeConverter()

        class JsonbNodeConverter : Converter<JSONB, JsonNode> {
            override fun from(t: JSONB?): JsonNode? = t?.let { mapper.readTree(it.toString()) }

            override fun to(u: JsonNode?): JSONB? = u?.let { JSONB.valueOf(mapper.writeValueAsString(it)) }

            override fun fromType() = JSONB::class.java

            override fun toType() = JsonNode::class.java
        }
    }

    override fun converter(): Converter<JSONB, JsonNode> = converter

    @Throws(SQLException::class)
    override fun sql(ctx: BindingSQLContext<JsonNode>) {
        when (ctx.render().paramType()) {
            ParamType.INLINED ->
                ctx.render()
                    .sql(ctx.variable())
                    .sql("::jsonb")
            else ->
                ctx.render()
                    .sql(ctx.variable())
                    .sql("::jsonb")
        }
    }

    @Throws(SQLException::class)
    override fun register(ctx: BindingRegisterContext<JsonNode>) {
        ctx.statement().registerOutParameter(ctx.index(), Types.VARCHAR)
    }

    @Throws(SQLException::class)
    override fun set(ctx: BindingSetStatementContext<JsonNode>) {
        ctx.statement().setString(ctx.index(), ctx.convert(converter()).value()?.toString())
    }

    @Throws(SQLException::class)
    override fun get(ctx: BindingGetResultSetContext<JsonNode>) {
        val obj = ctx.resultSet().getObject(ctx.index())
        val text =
            when (obj) {
                null -> null
                is org.postgresql.util.PGobject -> obj.value
                else -> obj.toString()
            }
        ctx.value(converter.from(text?.let { JSONB.valueOf(it) }))
    }

    @Throws(SQLException::class)
    override fun get(ctx: BindingGetStatementContext<JsonNode>) {
        val obj = ctx.statement().getObject(ctx.index())
        val text =
            when (obj) {
                null -> null
                is org.postgresql.util.PGobject -> obj.value
                else -> obj.toString()
            }
        ctx.value(converter.from(text?.let { JSONB.valueOf(it) }))
    }

    @Throws(SQLException::class)
    override fun set(ctx: BindingSetSQLOutputContext<JsonNode>) {
        throw SQLFeatureNotSupportedException()
    }

    @Throws(SQLException::class)
    override fun get(ctx: BindingGetSQLInputContext<JsonNode>) {
        throw SQLFeatureNotSupportedException()
    }
}
