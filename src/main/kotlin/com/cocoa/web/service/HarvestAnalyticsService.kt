package com.cocoa.web.service

import com.cocoa.web.exception.LocationHierarchyException
import com.cocoa.web.model.Analytics
import com.cocoa.web.repository.HarvestRepository
import com.cocoa.web.repository.LocationRepository
import com.cocoa.web.util.generateMonthRange
import org.springframework.stereotype.Service

@Service
class HarvestAnalyticsService(
    private val harvestRepository: HarvestRepository,
    private val locationRepository: LocationRepository,
) {
    private val allGrades = listOf("A", "B", "C")

    // Series
    fun getDeltaSeries(filter: Analytics.Query.HarvestFilter): Analytics.Report.TimeSeries {
        return buildReport(filter, "Harvest Weight per Month", "kg") {
            harvestRepository.fetchMonthlyDelta(filter)
        }
    }

    fun getCumulativeSeries(filter: Analytics.Query.HarvestFilter): Analytics.Report.TimeSeries {
        val report =
            buildReport(filter, "Running Total Harvest Weight per Month", "kg") {
                harvestRepository.fetchMonthlyDelta(filter)
            }

        val cumulativeEntries =
            report.series.map { entry ->
                var runningTotal = 0.0
                entry.copy(
                    values =
                        entry.values.map {
                            runningTotal += it.toDouble()
                            runningTotal
                        },
                )
            }
        return report.copy(series = cumulativeEntries)
    }

    fun getAverageSeries(filter: Analytics.Query.HarvestFilter): Analytics.Report.TimeSeries {
        return buildReport(filter, "Average Harvest Weight per Month", "kg") {
            harvestRepository.fetchMonthlyAvg(filter)
        }
    }

    fun getFrequencySeries(filter: Analytics.Query.HarvestFilter): Analytics.Report.TimeSeries {
        return buildReport(filter, "Number of Harvests per Month", "records") {
            harvestRepository.fetchMonthlyFreq(filter)
        }
    }

    // Summary
    fun getTotalSummary(filter: Analytics.Query.HarvestFilter): Analytics.Report.Summary {
        val total = harvestRepository.fetchTotalQuantity(filter)
        print(total)
        return buildSummary(filter, "Total Harvest Weight", "kg", total)
    }

    fun getFrequencySummary(filter: Analytics.Query.HarvestFilter): Analytics.Report.Summary {
        val count = harvestRepository.fetchTotalCount(filter)
        return buildSummary(filter, "Total Number of Harvests", "records", count.toDouble())
    }

    // Helper Functions
    private fun buildReport(
        filter: Analytics.Query.HarvestFilter,
        title: String,
        unit: String,
        fetcher: () -> List<HarvestRepository.MonthlyGradeRow>,
    ): Analytics.Report.TimeSeries {
        val isHierarchyConsistent =
            locationRepository.isHierarchyValid(
                provinceId = filter.provinceId,
                districtId = filter.districtId,
                subdistrictId = filter.subdistrictId,
                farmId = filter.farmId,
            )

        if (!isHierarchyConsistent) {
            throw LocationHierarchyException(
                "The geographic hierarchy provided (Province/District/Subdistrict/Farm) is inconsistent or invalid.",
            )
        }

        val monthRange = generateMonthRange(filter.from, filter.to)
        val rawData = fetcher()
        val metadata =
            locationRepository.enrichHierarchy(
                provinceId = filter.provinceId,
                districtId = filter.districtId,
                subdistrictId = filter.subdistrictId,
                farmId = filter.farmId,
            )

        val dataMap =
            rawData.groupBy { it.gradeCode }.mapValues { (_, rows) ->
                rows.associate { java.time.YearMonth.from(it.month) to it.value }
            }

        val gradesToInclude =
            filter.gradeCode?.let {
                listOf(it)
            } ?: allGrades

        val series =
            gradesToInclude.map { grade ->
                val gradeMonths = dataMap[grade] ?: emptyMap()

                val values =
                    monthRange.map { month ->
                        gradeMonths[month] ?: 0.0
                    }

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
            metadata = metadata,
            series = series,
        )
    }

    private fun buildSummary(
        filter: Analytics.Query.HarvestFilter,
        title: String,
        unit: String,
        value: Double,
    ): Analytics.Report.Summary {
        val isHierarchyConsistent =
            locationRepository.isHierarchyValid(
                provinceId = filter.provinceId,
                districtId = filter.districtId,
                subdistrictId = filter.subdistrictId,
                farmId = filter.farmId,
            )

        if (!isHierarchyConsistent) {
            throw LocationHierarchyException(
                "The geographic hierarchy provided (Province/District/Subdistrict/Farm) is inconsistent or invalid.",
            )
        }

        val label = filter.gradeCode?.let { "Grade $it" } ?: "All Grades"
        val metadata =
            locationRepository.enrichHierarchy(
                provinceId = filter.provinceId,
                districtId = filter.districtId,
                subdistrictId = filter.subdistrictId,
                farmId = filter.farmId,
            )

        return Analytics.Report.Summary(
            from = filter.from,
            to = filter.to,
            title = title,
            metadata = metadata,
            data =
                Analytics.Report.DataItem(
                    label = label,
                    value = value,
                    unit = unit,
                ),
        )
    }
}
