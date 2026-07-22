package com.cocoa.web.model

import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import java.time.YearMonth
import java.util.UUID

object Analytics {

    object Query {

        interface TimeRangeFilter {
            @get:Parameter(description = "Start month", example = "2026-01")
            @get:Schema(type = "string", pattern = "yyyy-MM")
            val from: YearMonth

            @get:Parameter(description = "End month", example = "2026-12")
            @get:Schema(type = "string", pattern = "yyyy-MM")
            val to: YearMonth
        }

        interface GeoFilter {
            @get:Schema(description = "Province UUID")
            val provinceId: UUID?
            @get:Schema(description = "District UUID")
            val districtId: UUID?
            @get:Schema(description = "Subdistrict UUID")
            val subdistrictId: UUID?
        }

        interface SpatialFilter {
            @get:Schema(description = "GeoJSON String")
            val geoJson: String?
        }

        data class SpatialHarvestQuery(
            override val from: YearMonth,
            override val to: YearMonth,
            val gradeCode: String? = null,
        ) : TimeRangeFilter

        data class SpatialPayload(
            val geoJson: String? = null,
        )

        data class SpatialHarvestFilter(
            override val from: YearMonth,
            override val to: YearMonth,
            override val geoJson: String? = null,
            val gradeCode: String? = null
        ) : TimeRangeFilter, SpatialFilter

        data class HarvestFilter(
            override val from: YearMonth,
            override val to: YearMonth,
            override val provinceId: UUID? = null,
            override val districtId: UUID? = null,
            override val subdistrictId: UUID? = null,
            @Schema(description = "Farm UUID")
            val farmId: UUID? = null,
            @Schema(description = "Harvest Grade (A, B, or C)", example = "A")
            val gradeCode: String? = null,
        ) : TimeRangeFilter, GeoFilter

        data class UserFilter(
            override val from: YearMonth,
            override val to: YearMonth,
            @Schema(description = "Filter by user role", example = "FARMER")
            val roleName: String? = null,
        ) : TimeRangeFilter
    }

    object Report {

        data class TimeSeries(
            @Schema(type = "string", example = "2026-01")
            val from: YearMonth,
            @Schema(type = "string", example = "2026-12")
            val to: YearMonth,
            @Schema(description = "Display title for the report chart")
            val title: String,
            @Schema(description = "Additional Metadata")
            val metadata: Map<String, Any?>,
            val series: List<SeriesData>
        )

        data class SeriesData(
            @Schema(description = "Label for the data line", example = "Grade A")
            val label: String,
            @Schema(description = "List of values corresponding to the month range")
            val values: List<Number>,
            @Schema(description = "Measurement unit", example = "kg")
            val unit: String?
        )

        data class Summary(
            @Schema(type = "string", example = "2026-01")
            val from: YearMonth,
            @Schema(type = "string", example = "2026-12")
            val to: YearMonth,
            @Schema(description = "Display title summarized data")
            val title: String,
            @Schema(description = "Additional Metadata")
            val metadata: Map<String, Any?>,
            val data: DataItem
        )

        data class DataItem(
            @Schema(description = "Label for the data")
            val label: String,
            @Schema(description = "Value of the data")
            val value: Number,
            @Schema(description = "Measurement unit", example = "kg")
            val unit: String?
        )

    }


}