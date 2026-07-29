package com.cocoa.web.service

import com.cocoa.web.model.Analytics
import com.cocoa.web.repository.UserRepository
import com.cocoa.web.util.generateMonthRange
import org.springframework.stereotype.Service
import java.time.YearMonth

@Service
class UserAnalyticsService(
    private val userRepository: UserRepository,
) {
    fun getCumulativeSeries(filter: Analytics.Query.UserFilter): Analytics.Report.TimeSeries {
        return buildUserSeries(
            filter = filter,
            title = "Running Total User per Month",
            isCumulative = true,
        )
    }

    fun getDeltaSeries(filter: Analytics.Query.UserFilter): Analytics.Report.TimeSeries {
        return buildUserSeries(
            filter = filter,
            title = "New User per Month",
            isCumulative = false,
        )
    }

    fun getCountSummary(filter: Analytics.Query.UserFilter): Analytics.Report.Summary {
        val total = userRepository.fetchTotalUserCount(filter)
        val label = filter.roleName?.let { "New $it Users" } ?: "Total New Users"

        return Analytics.Report.Summary(
            from = filter.from,
            to = filter.to,
            title = "Total User",
            metadata = emptyMap(),
            data =
                Analytics.Report.DataItem(
                    label = label,
                    value = total,
                    unit = "users",
                ),
        )
    }

    private fun buildUserSeries(
        filter: Analytics.Query.UserFilter,
        title: String,
        isCumulative: Boolean,
    ): Analytics.Report.TimeSeries {
        val monthRange = generateMonthRange(filter.from, filter.to)
        val rawData =
            userRepository.fetchMonthlyDelta(filter)
                .associate { YearMonth.from(it.month) to it.value }

        val deltaValues = monthRange.map { month -> rawData[month] ?: 0L }

        val finalValues =
            if (isCumulative) {
                var runningTotal = 0L
                deltaValues.map {
                    runningTotal += it
                    runningTotal
                }
            } else {
                deltaValues
            }

        return Analytics.Report.TimeSeries(
            from = filter.from,
            to = filter.to,
            title = title,
            metadata = emptyMap(),
            series =
                listOf(
                    Analytics.Report.SeriesData(
                        label = filter.roleName ?: "All Users",
                        values = finalValues.map { it.toDouble() },
                        unit = "users",
                    ),
                ),
        )
    }
}
