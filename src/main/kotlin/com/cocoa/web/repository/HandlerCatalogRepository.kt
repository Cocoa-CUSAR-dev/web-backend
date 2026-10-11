package com.cocoa.web.repository

import com.cocoa.web.base.BaseRepository
import com.cocoa.web.model.Handler
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap

@Repository
class HandlerCatalogRepository(
    dsl: DSLContext,
    private val handlerRegistryRepository: HandlerRegistryRepository,
) : BaseRepository(dsl) {
    // US2-13: the handler list now comes from form.handler_registry (V30)
    // instead of a hard-coded map that had to be kept in sync with Go's
    // standaloneHandlerTables and the chatbot's parent_picker by hand. The 10
    // handlers that map held are seeded with exactly the same tables, so they
    // behave as before; adding a handler is now a migration, not a code change.
    fun listHandlers(): List<String> {
        return handlerRegistryRepository.activeHandlers().map { it.handler }
    }

    fun findRegistration(handler: String): Handler.Registration? = handlerRegistryRepository.find(handler)

    // dsl.meta().tables walks the entire live schema -- expensive, same cost
    // FormRepository.fetchRefChoices already pays and caches. A handler's
    // column set only changes via a migration + restart, so it's safe to
    // cache here the same way. name/dataType still come from this exact call,
    // so the field list for every existing handler is unchanged.
    private val handlerFieldsCache = ConcurrentHashMap<String, List<Handler.Field>>()

    fun fetchHandlerFields(handler: String): List<Handler.Field> {
        val registration =
            handlerRegistryRepository.find(handler)
                ?: throw IllegalArgumentException("Unknown handler: $handler")

        return handlerFieldsCache.getOrPut(handler) {
            val table =
                dsl.meta().tables
                    .find { it.schema?.name == registration.schemaName && it.name == registration.tableName }
                    ?: throw IllegalArgumentException("Destination table not found for handler: $handler")

            val metadata = columnMetadata(registration.schemaName, registration.tableName)

            table.fields().map { field ->
                val column = metadata[field.name]
                Handler.Field(
                    name = field.name,
                    dataType = field.dataType.typeName,
                    required = column != null && isRequired(registration, field.name, column),
                    references = column?.references,
                    dependsOn = handlerRegistryRepository.dependencyFor(field.name)?.parentField,
                    allowedInputTypes = column?.let(::allowedInputTypes) ?: emptyList(),
                )
            }
        }
    }

    // What the builder needs to know about each column, in one targeted
    // information_schema query (not another dsl.meta() walk).
    private data class ColumnMetadata(
        val dataType: String,
        val notNullWithoutDefault: Boolean,
        val references: String?,
    )

    private fun columnMetadata(
        schema: String,
        table: String,
    ): Map<String, ColumnMetadata> {
        val references =
            dsl.resultQuery(
                """
                SELECT kcu.column_name, ccu.table_schema || '.' || ccu.table_name
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                  ON kcu.constraint_name = tc.constraint_name AND kcu.table_schema = tc.table_schema
                JOIN information_schema.constraint_column_usage ccu
                  ON ccu.constraint_name = tc.constraint_name
                WHERE tc.constraint_type = 'FOREIGN KEY' AND tc.table_schema = ? AND tc.table_name = ?
                """.trimIndent(),
                schema,
                table,
            ).fetch { it.get(0, String::class.java) to it.get(1, String::class.java) }
                .toMap()

        return dsl.select(
            DSL.field(DSL.name("column_name"), String::class.java),
            DSL.field(DSL.name("data_type"), String::class.java),
            DSL.field(DSL.name("is_nullable"), String::class.java),
            DSL.field(DSL.name("column_default"), String::class.java),
        )
            .from(DSL.table(DSL.name("information_schema", "columns")))
            .where(DSL.field(DSL.name("table_schema")).eq(schema))
            .and(DSL.field(DSL.name("table_name")).eq(table))
            .fetch { record ->
                val name = record.value1()
                name to
                    ColumnMetadata(
                        dataType = record.value2(),
                        notNullWithoutDefault = record.value3() == "NO" && record.value4() == null,
                        references = references[name],
                    )
            }
            .toMap()
    }

    // Required = the INSERT fails without it. A child handler's parent_field
    // is NOT NULL but exempt: the chatbot's parent picker supplies it, so the
    // form never asks it. extra_required_fields covers a nullable column the
    // team still wants asked every time (farm_economic_eval.farm_id).
    private fun isRequired(
        registration: Handler.Registration,
        column: String,
        metadata: ColumnMetadata,
    ): Boolean {
        if (column in registration.extraRequiredFields) return true
        return metadata.notNullWithoutDefault && column != registration.parentField
    }

    // Aggregate counts for the Data Map page (GET /forms/handlers/catalog),
    // three cheap GROUP BY queries -- the service caches the result 5 min.
    data class CatalogStats(
        val formSummaries: Map<String, Handler.Catalog.FormSummary>,
        val responseCounts: Map<String, Int>,
        // handler -> field_name -> number of that handler's forms asking it
        val fieldUsage: Map<String, Map<String, Int>>,
    )

    fun fetchCatalogStats(): CatalogStats {
        val counts =
            dsl.resultQuery(
                "SELECT handler, count(*) FROM form.task_form GROUP BY handler",
            ).fetch { it.get(0, String::class.java) to it.get(1, Int::class.java) }
                .toMap()

        val latest =
            dsl.resultQuery(
                """
                SELECT DISTINCT ON (tf.handler) tf.handler, t.title, t.open_at
                FROM form.task_form tf
                JOIN form.task t ON t.task_id = tf.task_id
                ORDER BY tf.handler, t.open_at DESC NULLS LAST
                """.trimIndent(),
            ).fetch {
                it.get(0, String::class.java) to
                    (it.get(1, String::class.java) to it.get(2, java.time.LocalDateTime::class.java))
            }.toMap()

        val formSummaries =
            counts.mapValues { (handler, count) ->
                Handler.Catalog.FormSummary(
                    count = count,
                    latestTitle = latest[handler]?.first,
                    latestOpenAt = latest[handler]?.second,
                )
            }

        // Every response carries task_form_id (database V25 backfilled the
        // old ones), so this is a direct join.
        val responseCounts =
            dsl.resultQuery(
                """
                SELECT tf.handler, count(*)
                FROM form.response r
                JOIN form.task_form tf ON tf.form_id = r.task_form_id
                GROUP BY tf.handler
                """.trimIndent(),
            ).fetch { it.get(0, String::class.java) to it.get(1, Int::class.java) }
                .toMap()

        // Only active sections/questions -- what a farmer is actually asked.
        val fieldUsage =
            dsl.resultQuery(
                """
                SELECT tf.handler, q.field_name, count(DISTINCT tf.form_id)
                FROM form.task_form tf
                JOIN form.section s ON s.form_id = tf.form_id AND COALESCE(s.is_active, true)
                JOIN form.question q ON q.section_id = s.section_id AND COALESCE(q.is_active, true)
                WHERE q.field_name IS NOT NULL
                GROUP BY tf.handler, q.field_name
                """.trimIndent(),
            ).fetch {
                Triple(it.get(0, String::class.java), it.get(1, String::class.java), it.get(2, Int::class.java))
            }.groupBy({ it.first }, { it.second to it.third })
                .mapValues { (_, pairs) -> pairs.toMap() }

        return CatalogStats(formSummaries, responseCounts, fieldUsage)
    }

    companion object {
        // Tables filled by the app's registration flows (dedicated endpoints
        // with multi-table side effects), not by forms. Shown greyed on the
        // Data Map so researchers see the whole databank, not only what a
        // form can write. Static: these aren't handlers and won't become ones
        // in this design (doc §7 q2).
        val REGISTRATION_TABLES =
            listOf(
                Handler.Catalog.RegistrationTable("agriculture.farmer", "เกษตรกร"),
                Handler.Catalog.RegistrationTable("agriculture.farm", "ฟาร์ม"),
                Handler.Catalog.RegistrationTable("agriculture.plot", "แปลง"),
                Handler.Catalog.RegistrationTable("processing.hub", "จุดรวบรวม"),
                Handler.Catalog.RegistrationTable("processing.processing_station", "สถานีแปรรูป"),
            )

        // Which question input types a column can hold, first being the
        // builder's default. Column type drives this, not the researcher: a
        // free-text answer can never be inserted into a uuid column, which is
        // exactly how two dev forms broke. A varchar FK (grade_code ->
        // ref.grade_constant) is the one varchar that also takes OPTION.
        // An unknown column type allows nothing, and the save check then
        // reports it rather than guessing.
        private fun allowedInputTypes(column: ColumnMetadata): List<String> =
            allowedInputTypes(column.dataType, isForeignKey = column.references != null)

        fun allowedInputTypes(
            dataType: String,
            isForeignKey: Boolean,
        ): List<String> =
            when {
                dataType == "uuid" -> listOf("OPTION")
                dataType in setOf("integer", "bigint", "smallint") -> listOf("INT")
                dataType in setOf("numeric", "real", "double precision") -> listOf("FLOAT", "INT")
                dataType == "boolean" -> listOf("BOOLEAN")
                dataType == "date" -> listOf("DATE")
                dataType.startsWith("timestamp") -> listOf("DATETIME", "DATE")
                dataType in setOf("character varying", "text") && isForeignKey -> listOf("OPTION", "VARCHAR")
                dataType in setOf("character varying", "text") -> listOf("VARCHAR")
                else -> emptyList()
            }
    }
}
