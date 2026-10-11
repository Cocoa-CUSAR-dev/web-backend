package com.cocoa.web.service

import com.cocoa.web.model.Handler
import com.cocoa.web.repository.HandlerCatalogRepository
import com.cocoa.web.service.FormDefinitionValidator.QuestionSpec
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

// US2-13 (docs-and-plan#241/#244): the save rules, against the real shapes of
// two handlers -- farm_activity (standalone, farm_id + farm_activity_type_id
// required, plot_id depends on farm_id) and farm_activity_fertilizer (a child
// handler whose farm_activity_id the chatbot's picker supplies).
class FormDefinitionValidatorTest {
    private val farmActivity =
        Handler.Registration(
            handler = "farm_activity",
            schemaName = "agriculture",
            tableName = "farm_activity",
            labelTh = "กิจกรรมในสวน",
            descriptionTh = "",
            category = "garden",
            frequency = "daily",
            parentHandler = null,
            parentField = null,
            extraRequiredFields = emptyList(),
            sortOrder = 10,
        )

    private val farmActivityFields =
        listOf(
            field("farm_activity_id", "uuid", required = false),
            field("farm_id", "uuid", required = true),
            field("plot_id", "uuid", required = false, dependsOn = "farm_id"),
            field("farm_activity_type_id", "uuid", required = true),
            field("description", "character varying", required = false),
        )

    private val fertilizer =
        farmActivity.copy(
            handler = "farm_activity_fertilizer",
            tableName = "farm_activity_fertilizer",
            parentHandler = "farm_activity",
            parentField = "farm_activity_id",
        )

    // farm_activity_id is NOT NULL on the table, but `required` already
    // excludes it for a child handler -- the repository does that, so the
    // fixture mirrors it.
    private val fertilizerFields =
        listOf(
            field("farm_activity_id", "uuid", required = false),
            field("fertilizer_id", "uuid", required = true),
        )

    private val plotNeedsFarm =
        Handler.Dependency("plot_id", "farm_id", "agriculture.plot", "farm_id", "แปลงต้องอยู่ในฟาร์มที่เลือก")

    private fun field(
        name: String,
        dataType: String,
        required: Boolean,
        dependsOn: String? = null,
    ) = Handler.Field(
        name = name,
        dataType = dataType,
        required = required,
        dependsOn = dependsOn,
        allowedInputTypes = HandlerCatalogRepository.allowedInputTypes(dataType, isForeignKey = dataType == "uuid"),
    )

    private fun q(
        fieldName: String?,
        inputType: String = "OPTION",
        order: Int,
        section: Int = 0,
        label: String = fieldName ?: "คำถาม",
    ) = QuestionSpec(section, order, label, inputType, fieldName)

    private fun validate(vararg questions: QuestionSpec) =
        FormDefinitionValidator.validate(farmActivity, farmActivityFields, listOf(plotNeedsFarm), questions.toList())

    @Test
    fun `a correct farm_activity form passes`() {
        val errors =
            validate(
                q("farm_id", order = 0),
                q("plot_id", order = 1),
                q("farm_activity_type_id", order = 2),
                q("description", inputType = "VARCHAR", order = 3),
            )

        assertThat(errors).isEmpty()
    }

    @Test
    fun `missing a required column is rejected and names the column`() {
        val errors = validate(q("farm_id", order = 0), q("description", inputType = "VARCHAR", order = 1))

        assertThat(errors).singleElement().asString()
            .contains("agriculture.farm_activity")
            .contains("farm_activity_type_id")
    }

    @Test
    fun `plot without a farm question is rejected`() {
        val errors = validate(q("plot_id", order = 0), q("farm_activity_type_id", order = 1))

        assertThat(errors).anySatisfy { assertThat(it).contains("plot_id").contains("farm_id").contains("อยู่ก่อน") }
    }

    @Test
    fun `farm asked after plot is rejected`() {
        val errors =
            validate(
                q("plot_id", order = 0),
                q("farm_id", order = 1),
                q("farm_activity_type_id", order = 2),
            )

        assertThat(errors).singleElement().asString().contains("plot_id").contains("farm_id")
    }

    @Test
    fun `order is by section first, then question`() {
        // farm_id is question 5 of section 0, plot_id question 0 of section 1:
        // farm still comes first.
        val errors =
            validate(
                q("farm_id", order = 5, section = 0),
                q("farm_activity_type_id", order = 6, section = 0),
                q("plot_id", order = 0, section = 1),
            )

        assertThat(errors).isEmpty()
    }

    @Test
    fun `an inputType the column can't hold is rejected`() {
        // The real dev bug: farm_activity_type_id (uuid) asked as free text.
        val errors =
            validate(
                q("farm_id", order = 0),
                q("farm_activity_type_id", inputType = "VARCHAR", order = 1),
            )

        assertThat(errors).singleElement().asString().contains("farm_activity_type_id").contains("VARCHAR")
    }

    @Test
    fun `the same field asked twice is rejected`() {
        val errors =
            validate(
                q("farm_id", order = 0),
                q("farm_activity_type_id", order = 1),
                q("description", inputType = "VARCHAR", order = 2, label = "รายละเอียด 1"),
                q("description", inputType = "VARCHAR", order = 3, label = "รายละเอียด 2"),
            )

        assertThat(errors).singleElement().asString().contains("description").contains("ซ้ำ")
    }

    @Test
    fun `a field that is not a column is allowed -- kept in form response only, as before`() {
        val errors =
            validate(
                q("farm_id", order = 0),
                q("farm_activity_type_id", order = 1),
                q("gis", inputType = "GEODATA", order = 2),
                q(null, inputType = "VARCHAR", order = 3),
            )

        assertThat(errors).isEmpty()
    }

    @Test
    fun `every problem is reported at once`() {
        val errors =
            validate(
                q("plot_id", order = 0),
                q("description", inputType = "DATE", order = 1),
            )

        assertThat(errors).hasSize(3) // inputType, missing required, plot before farm
    }

    @Test
    fun `a child handler's parent_field is exempt -- the chatbot picker supplies it`() {
        val errors =
            FormDefinitionValidator.validate(
                fertilizer,
                fertilizerFields,
                listOf(plotNeedsFarm),
                listOf(q("fertilizer_id", order = 0)),
            )

        assertThat(errors).isEmpty()
    }

    @Test
    fun `extra_required_fields makes a nullable column required`() {
        // farm_economic_eval.farm_id: nullable column, required by the registry.
        val economicEval =
            farmActivity.copy(
                handler = "farm_economic_eval",
                tableName = "farm_economic_eval",
                extraRequiredFields = listOf("farm_id"),
            )
        val fields =
            listOf(
                // required=true here because the repository folds
                // extra_required_fields into `required`.
                field("farm_id", "uuid", required = true),
                field("cocoa_price", "numeric", required = false),
            )

        val errors =
            FormDefinitionValidator.validate(
                economicEval,
                fields,
                emptyList(),
                listOf(q("cocoa_price", inputType = "FLOAT", order = 0)),
            )

        assertThat(errors).singleElement().asString().contains("farm_id")
    }
}
