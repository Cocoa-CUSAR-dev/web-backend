package com.cocoa.web.model

import java.time.LocalDateTime

object Handler {
    // One destination-table column, as the form builder sees it. name/dataType
    // are unchanged from before US2-13; the rest is what the builder needs so
    // a researcher can't build a form that can never be saved:
    // - required: NOT NULL with no default (minus a child handler's
    //   parent_field, which the chatbot's picker supplies), plus the
    //   registry's extra_required_fields;
    // - references: "schema.table" this column is a foreign key to, if any;
    // - dependsOn: the field this one's choices are filtered by
    //   (form.field_dependency), e.g. plot_id -> "farm_id";
    // - allowedInputTypes: the question input types this column can actually
    //   hold, first one being the sensible default. Kotlin rejects anything
    //   else on save.
    data class Field(
        val name: String,
        val dataType: String,
        val required: Boolean = false,
        val references: String? = null,
        val dependsOn: String? = null,
        val allowedInputTypes: List<String> = emptyList(),
    )

    // A form.handler_registry row (V30). Rows are added by migration only.
    data class Registration(
        val handler: String,
        val schemaName: String,
        val tableName: String,
        val labelTh: String,
        val descriptionTh: String,
        val category: String,
        val frequency: String,
        val parentHandler: String?,
        val parentField: String?,
        val extraRequiredFields: List<String>,
        val sortOrder: Int,
    ) {
        val qualifiedTable: String get() = "$schemaName.$tableName"
    }

    // A form.field_dependency row (V30): childField's chosen value must be a
    // row of linkTable whose linkColumn equals parentField's chosen value.
    data class Dependency(
        val childField: String,
        val parentField: String,
        val linkTable: String,
        val linkColumn: String,
        val labelTh: String,
    )

    // GET /forms/handlers/catalog -- everything the Data Map page shows.
    object Catalog {
        data class Response(
            val handlers: List<Entry>,
            val dependencies: List<Dependency>,
            val registrationTables: List<RegistrationTable>,
        )

        data class Entry(
            val handler: String,
            val labelTh: String,
            val descriptionTh: String,
            val category: String,
            val frequency: String,
            val table: String,
            val parentHandler: String?,
            val parentField: String?,
            val fields: List<CatalogField>,
            val forms: FormSummary,
            val responseCount: Int,
        )

        data class CatalogField(
            val name: String,
            val dataType: String,
            val required: Boolean,
            val references: String?,
            val dependsOn: String?,
            val allowedInputTypes: List<String>,
            // How many of this handler's forms ask this field (an active
            // question with this field_name).
            val usedByForms: Int,
        )

        data class FormSummary(
            val count: Int,
            val latestTitle: String?,
            val latestOpenAt: LocalDateTime?,
        )

        // Tables filled by registration flows in the app, not by forms --
        // shown greyed on the Data Map so researchers see the whole databank.
        data class RegistrationTable(
            val table: String,
            val labelTh: String,
        )
    }
}
