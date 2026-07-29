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
import org.jooq.JSON
import org.jooq.conf.ParamType
import org.jooq.impl.DSL
import java.sql.SQLException
import java.sql.SQLFeatureNotSupportedException
import java.sql.Types

class JsonNodeBinding : Binding<JSON, JsonNode> {
    companion object {
        private val mapper = ObjectMapper().registerKotlinModule()
        val converter = JsonNodeConverter()

        class JsonNodeConverter : Converter<JSON, JsonNode> {
            override fun from(t: JSON?): JsonNode? = t?.let { mapper.readTree(it.toString()) }

            override fun to(u: JsonNode?): JSON? = u?.let { JSON.valueOf(mapper.writeValueAsString(it)) }

            override fun fromType() = JSON::class.java

            override fun toType() = JsonNode::class.java
        }
    }

    override fun converter(): Converter<JSON, JsonNode> = converter

    @Throws(SQLException::class)
    override fun sql(ctx: BindingSQLContext<JsonNode>) {
        when (ctx.render().paramType()) {
            ParamType.INLINED ->
                ctx.render()
                    .visit(DSL.inline(ctx.convert(converter()).value()))
                    .sql("::json")
            else ->
                ctx.render()
                    .sql(ctx.variable())
                    .sql("::json")
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
        ctx.convert(converter()).value(JSON.valueOf(ctx.resultSet().getString(ctx.index())))
    }

    @Throws(SQLException::class)
    override fun get(ctx: BindingGetStatementContext<JsonNode>) {
        ctx.convert(converter()).value(JSON.valueOf(ctx.statement().getString(ctx.index())))
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
