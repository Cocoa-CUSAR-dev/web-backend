package com.cocoa.web.service

import com.cocoa.web.model.Analytics
import com.cocoa.web.repository.HarvestRepository
import com.cocoa.web.util.generateMonthRange
import org.springframework.stereotype.Service
import java.time.YearMonth

@Service
class SpatialHarvestAnalyticsService(
    private val harvestRepository: HarvestRepository,
) {
    private val allGrades = listOf("A", "B", "C")

    // --- Series ---

    fun getSpatialDeltaSeries(filter: Analytics.Query.SpatialHarvestFilter): Analytics.Report.TimeSeries {
        return buildSpatialReport(filter, "Harvest Weight per Month", "kg") {
            harvestRepository.fetchSpatialMonthlyDelta(filter)
        }
    }

    fun getSpatialCumulativeSeries(filter: Analytics.Query.SpatialHarvestFilter): Analytics.Report.TimeSeries {
        val report =
            buildSpatialReport(filter, "Running Total Harvest Weight per Month", "kg") {
                harvestRepository.fetchSpatialMonthlyDelta(filter)
            }

        val cumulativeEntries =
            report.series.map { entry ->
                var runningTotal = 0.0
                val cumulativeValues =
                    entry.values.map {
                        runningTotal += it.toDouble()
                        runningTotal
                    }
                entry.copy(values = cumulativeValues)
            }
        return report.copy(series = cumulativeEntries)
    }

    fun getSpatialAverageSeries(filter: Analytics.Query.SpatialHarvestFilter): Analytics.Report.TimeSeries {
        return buildSpatialReport(filter, "Average Harvest Weight per Month", "kg") {
            harvestRepository.fetchSpatialMonthlyAvg(filter)
        }
    }

    fun getSpatialFrequencySeries(filter: Analytics.Query.SpatialHarvestFilter): Analytics.Report.TimeSeries {
        return buildSpatialReport(filter, "Number of Harvests per Month", "records") {
            harvestRepository.fetchSpatialMonthlyFreq(filter)
        }
    }

    // --- Summaries ---

    fun getSpatialTotalSummary(filter: Analytics.Query.SpatialHarvestFilter): Analytics.Report.Summary {
        val total = harvestRepository.fetchSpatialTotalQuantity(filter)
        return buildSpatialSummary(filter, "Total Harvest Weight", "kg", total)
    }

    fun getSpatialFrequencySummary(filter: Analytics.Query.SpatialHarvestFilter): Analytics.Report.Summary {
        val count = harvestRepository.fetchSpatialTotalCount(filter)
        return buildSpatialSummary(filter, "Total Number of Harvests", "records", count.toDouble())
    }

    // --- Helper Functions ---

    private fun buildSpatialReport(
        filter: Analytics.Query.SpatialHarvestFilter,
        title: String,
        unit: String,
        fetcher: () -> List<HarvestRepository.MonthlyGradeRow>,
    ): Analytics.Report.TimeSeries {
        val monthRange = generateMonthRange(filter.from, filter.to)
        val rawData = fetcher()

        val dataMap =
            rawData.groupBy { it.gradeCode }.mapValues { (_, rows) ->
                rows.associate { YearMonth.from(it.month) to it.value }
            }

        val gradesToInclude = filter.gradeCode?.let { listOf(it) } ?: allGrades

        val series =
            gradesToInclude.map { grade ->
                val gradeMonths = dataMap[grade] ?: emptyMap()
                val values = monthRange.map { month -> gradeMonths[month] ?: 0.0 }

                Analytics.Report.SeriesData(
                    label = "Grade $grade",
                    values = values,
                    unit = unit,
                )
            }

        return Analytics.Report.TimeSeries(
            from = filter.from,
            to = filter.to,
            title = title,
            metadata = mapOf("type" to "spatial_selection"),
            series = series,
        )
    }

    private fun buildSpatialSummary(
        filter: Analytics.Query.SpatialHarvestFilter,
        title: String,
        unit: String,
        value: Double,
    ): Analytics.Report.Summary {
        val label = filter.gradeCode?.let { "Grade $it" } ?: "All Grades"

        return Analytics.Report.Summary(
            from = filter.from,
            to = filter.to,
            title = title,
            metadata = mapOf("type" to "spatial_selection"),
            data =
                Analytics.Report.DataItem(
                    label = label,
                    value = value,
                    unit = unit,
                ),
        )
    }
}
