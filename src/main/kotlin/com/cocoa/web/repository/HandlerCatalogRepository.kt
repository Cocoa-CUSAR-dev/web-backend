package com.cocoa.web.repository

import com.cocoa.web.base.BaseRepository
import com.cocoa.web.model.Handler
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap

@Repository
class HandlerCatalogRepository(
    dsl: DSLContext,
) : BaseRepository(dsl) {
    // The 10 handler values verified against the live DB (see
    // docs/bugs/task-dissection-design.md's classification table). This is
    // domain vocabulary, not something live schema introspection alone can
    // derive -- each schema also has plenty of *_constant/reference tables
    // that are NOT valid submission targets, so "which tables exist" isn't
    // the same question as "which handlers are valid". Keep this in sync
    // with mobile-backend's standaloneHandlerTables if either changes.
    private val knownHandlerSchemas =
        mapOf(
            "farm_activity" to "agriculture",
            "processing_record" to "processing",
            "farm_pest_disease_record" to "agriculture",
            "harvest" to "collection",
            "batch" to "processing",
            "farm_activity_fertilizer" to "agriculture",
            "farm_activity_chemical" to "agriculture",
            "harvest_grade_detail" to "collection",
            "fermentation_batch" to "processing",
            "drying_batch" to "processing",
        )

    fun listHandlers(): List<String> {
        return knownHandlerSchemas.keys.toList()
    }

    // dsl.meta().tables walks the entire live schema -- expensive, same cost
    // FormRepository.fetchRefChoices already pays and caches. A handler's
    // column set only changes via a migration + restart, so it's safe to
    // cache here the same way.
    private val handlerFieldsCache = ConcurrentHashMap<String, List<Handler.Field>>()

    fun fetchHandlerFields(handler: String): List<Handler.Field> {
        val schema =
            knownHandlerSchemas[handler]
                ?: throw IllegalArgumentException("Unknown handler: $handler")

        return handlerFieldsCache.getOrPut(handler) {
            val table =
                dsl.meta().tables
                    .find { it.schema?.name == schema && it.name == handler }
                    ?: throw IllegalArgumentException("Destination table not found for handler: $handler")

            table.fields().map { Handler.Field(name = it.name, dataType = it.dataType.typeName) }
        }
    }
}
