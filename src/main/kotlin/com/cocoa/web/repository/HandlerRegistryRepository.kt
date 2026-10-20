package com.cocoa.web.repository

import com.cocoa.generated.form.Tables.FIELD_DEPENDENCY
import com.cocoa.generated.form.Tables.HANDLER_REGISTRY
import com.cocoa.web.base.BaseRepository
import com.cocoa.web.model.Handler
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import java.time.Duration
import java.time.Instant

// US2-13 (docs-and-plan#241): form.handler_registry and form.field_dependency
// (database V30) -- the single source of truth that replaced the hard-coded
// handler map this service, Go and the chatbot each kept by hand.
//
// Read through a small in-process cache (5-minute TTL, the same shape as Go's
// formSchemaCache): these rows change only by migration, so re-reading them on
// every form request would be pure overhead, but a TTL rather than a forever
// cache means a newly migrated handler shows up without a restart.
@Repository
class HandlerRegistryRepository(
    dsl: DSLContext,
) : BaseRepository(dsl) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    private data class Snapshot(
        val handlers: List<Handler.Registration>,
        val dependencies: List<Handler.Dependency>,
        val loadedAt: Instant,
    )

    @Volatile
    private var snapshot: Snapshot? = null

    // Active handlers, in registry sort_order. A row whose identifiers fail
    // the pattern check or whose table doesn't exist is left out (and logged)
    // rather than failing every caller -- the migration's own guard should
    // already have stopped it, so this is defence in depth.
    fun activeHandlers(): List<Handler.Registration> = current().handlers

    fun find(handler: String): Handler.Registration? = current().handlers.firstOrNull { it.handler == handler }

    fun dependencies(): List<Handler.Dependency> = current().dependencies

    fun dependencyFor(childField: String): Handler.Dependency? = current().dependencies.firstOrNull { it.childField == childField }

    private fun current(): Snapshot {
        val cached = snapshot
        if (cached != null && Duration.between(cached.loadedAt, Instant.now()) < TTL) {
            return cached
        }
        return synchronized(this) {
            val again = snapshot
            if (again != null && Duration.between(again.loadedAt, Instant.now()) < TTL) {
                again
            } else {
                load().also { snapshot = it }
            }
        }
    }

    private fun load(): Snapshot {
        val existingTables = existingTables()

        val handlers =
            dsl.selectFrom(HANDLER_REGISTRY)
                .where(HANDLER_REGISTRY.IS_ACTIVE.isTrue)
                .orderBy(HANDLER_REGISTRY.SORT_ORDER, HANDLER_REGISTRY.HANDLER)
                .fetch { record ->
                    Handler.Registration(
                        handler = record.handler,
                        schemaName = record.schemaName,
                        tableName = record.tableName,
                        labelTh = record.labelTh,
                        descriptionTh = record.descriptionTh,
                        category = record.category,
                        frequency = record.frequency,
                        parentHandler = record.parentHandler,
                        parentField = record.parentField,
                        extraRequiredFields = record.extraRequiredFields?.filterNotNull()?.toList() ?: emptyList(),
                        sortOrder = record.sortOrder,
                    )
                }
                .filter { registration ->
                    val identifiers = listOfNotNull(registration.schemaName, registration.tableName, registration.parentField)
                    when {
                        identifiers.any { !isIdentifier(it) } -> {
                            logger.error("handler_registry row '${registration.handler}' has an invalid identifier -- ignored")
                            false
                        }
                        registration.qualifiedTable !in existingTables -> {
                            logger.error(
                                "handler_registry row '${registration.handler}' points at missing table " +
                                    "${registration.qualifiedTable} -- ignored",
                            )
                            false
                        }
                        else -> true
                    }
                }

        val dependencies =
            dsl.selectFrom(FIELD_DEPENDENCY)
                .orderBy(FIELD_DEPENDENCY.CHILD_FIELD, FIELD_DEPENDENCY.PARENT_FIELD)
                .fetch { record ->
                    Handler.Dependency(
                        childField = record.childField,
                        parentField = record.parentField,
                        linkTable = record.linkTable,
                        linkColumn = record.linkColumn,
                        labelTh = record.labelTh,
                    )
                }
                .filter { dependency ->
                    val identifiersOk =
                        isIdentifier(dependency.childField) && isIdentifier(dependency.parentField) &&
                            isIdentifier(dependency.linkColumn) &&
                            dependency.linkTable.split(".").let { it.size == 2 && it.all(::isIdentifier) }
                    when {
                        !identifiersOk -> {
                            logger.error("field_dependency row '${dependency.childField}' has an invalid identifier -- ignored")
                            false
                        }
                        dependency.linkTable !in existingTables -> {
                            logger.error(
                                "field_dependency row '${dependency.childField}' points at missing table " +
                                    "${dependency.linkTable} -- ignored",
                            )
                            false
                        }
                        else -> true
                    }
                }

        return Snapshot(handlers, dependencies, Instant.now())
    }

    // One cheap information_schema query for every base table, rather than
    // one per registry row.
    private fun existingTables(): Set<String> =
        dsl.select(
            DSL.field(DSL.name("table_schema"), String::class.java),
            DSL.field(DSL.name("table_name"), String::class.java),
        )
            .from(DSL.table(DSL.name("information_schema", "tables")))
            .fetch { "${it.value1()}.${it.value2()}" }
            .toSet()

    companion object {
        val TTL: Duration = Duration.ofMinutes(5)

        private val IDENTIFIER = Regex("^[a-z_][a-z0-9_]*$")

        // schema/table/column names from the registry end up in dynamic SQL
        // (here, and in Go's INSERT), so they're checked against a strict
        // pattern even though only migrations can write them.
        fun isIdentifier(value: String): Boolean = IDENTIFIER.matches(value)
    }
}
