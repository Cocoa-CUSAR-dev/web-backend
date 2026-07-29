package com.cocoa.web.controller

import com.cocoa.web.base.BaseController
import com.cocoa.web.model.Analytics
import com.cocoa.web.model.ApiResponse
import com.cocoa.web.model.toResponseEntity
import com.cocoa.web.service.HarvestAnalyticsService
import com.cocoa.web.service.UserAnalyticsService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/analytics")
@Tag(name = "Analytics", description = "Time-series reports for harvests and user growth")
class AnalyticsController(
    private val harvestService: HarvestAnalyticsService,
    private val userService: UserAnalyticsService,
) : BaseController() {
    // Harvest
    @PreAuthorize("hasAuthority('read:report:all')")
    @Operation(
        summary = "Get cumulative time series",
        description = "Fetch time series harvest of data accumulating from past month within time range.",
    )
    @GetMapping("/harvest/time-series/sum")
    fun getHarvestTimeSeriesSum(filter: Analytics.Query.HarvestFilter): ResponseEntity<ApiResponse<Analytics.Report.TimeSeries>> {
        require(!filter.from.isAfter(filter.to)) { "'from' must not be after 'to'" }

        val report = harvestService.getCumulativeSeries(filter)
        return report.toResponseEntity(HttpStatus.OK)
    }

    @PreAuthorize("hasAuthority('read:report:all')")
    @Operation(
        summary = "Get incremental time series",
        description = "Fetch time series of harvest data incrementing from past month within time range.",
    )
    @GetMapping("/harvest/time-series/delta")
    fun getHarvestTimeSeriesDelta(filter: Analytics.Query.HarvestFilter): ResponseEntity<ApiResponse<Analytics.Report.TimeSeries>> {
        require(!filter.from.isAfter(filter.to)) { "'from' must not be after 'to'" }

        val report = harvestService.getDeltaSeries(filter)
        return report.toResponseEntity(HttpStatus.OK)
    }

    @PreAuthorize("hasAuthority('read:report:all')")
    @Operation(
        summary = "Get mean time series",
        description = "Fetch time series of harvest data averaging amount in each month within time range.",
    )
    @GetMapping("/harvest/time-series/average")
    fun getHarvestTimeSeriesAverage(filter: Analytics.Query.HarvestFilter): ResponseEntity<ApiResponse<Analytics.Report.TimeSeries>> {
        require(!filter.from.isAfter(filter.to)) { "'from' must not be after 'to'" }

        val report = harvestService.getAverageSeries(filter)
        return report.toResponseEntity(HttpStatus.OK)
    }

    @PreAuthorize("hasAuthority('read:report:all')")
    @Operation(
        summary = "Get count time series",
        description = "Fetch time series of harvest data counting record in each month within time range.",
    )
    @GetMapping("/harvest/time-series/frequency")
    fun getHarvestTimeSeriesFrequency(filter: Analytics.Query.HarvestFilter): ResponseEntity<ApiResponse<Analytics.Report.TimeSeries>> {
        require(!filter.from.isAfter(filter.to)) { "'from' must not be after 'to'" }

        val report = harvestService.getFrequencySeries(filter)
        return report.toResponseEntity(HttpStatus.OK)
    }

    @PreAuthorize("hasAuthority('read:report:all')")
    @Operation(summary = "Get total summary", description = "Fetch summary of harvest data for total quantity within time range.")
    @GetMapping("/harvest/summary/total")
    fun getHarvestSummaryTotal(filter: Analytics.Query.HarvestFilter): ResponseEntity<ApiResponse<Analytics.Report.Summary>> {
        require(!filter.from.isAfter(filter.to)) { "'from' must not be after 'to'" }

        val report = harvestService.getTotalSummary(filter)
        return report.toResponseEntity(HttpStatus.OK)
    }

    @PreAuthorize("hasAuthority('read:report:all')")
    @Operation(summary = "Get count summary", description = "Fetch summary of harvest data for frequency count within time range.")
    @GetMapping("/harvest/summary/count")
    fun getHarvestSummaryCount(filter: Analytics.Query.HarvestFilter): ResponseEntity<ApiResponse<Analytics.Report.Summary>> {
        require(!filter.from.isAfter(filter.to)) { "'from' must not be after 'to'" }

        val report = harvestService.getFrequencySummary(filter)
        return report.toResponseEntity(HttpStatus.OK)
    }

    // User
    @PreAuthorize("hasAuthority('read:report:all')")
    @Operation(
        summary = "Get cumulative time series",
        description = "Fetch time series user data accumulating from past month within time range.",
    )
    @GetMapping("/users/time-series/sum")
    fun getUserTimeSeriesSum(filter: Analytics.Query.UserFilter): ResponseEntity<ApiResponse<Analytics.Report.TimeSeries>> {
        require(!filter.from.isAfter(filter.to)) { "'from' must not be after 'to'" }

        val report = userService.getCumulativeSeries(filter)
        return report.toResponseEntity(HttpStatus.OK)
    }

    @PreAuthorize("hasAuthority('read:report:all')")
    @Operation(
        summary = "Get incremental time series",
        description = "Fetch time series user data incrementing from past month within time range.",
    )
    @GetMapping("/users/time-series/delta")
    fun getUserTimeSeriesDelta(filter: Analytics.Query.UserFilter): ResponseEntity<ApiResponse<Analytics.Report.TimeSeries>> {
        require(!filter.from.isAfter(filter.to)) { "'from' must not be after 'to'" }

        val report = userService.getDeltaSeries(filter)
        return report.toResponseEntity(HttpStatus.OK)
    }

    @PreAuthorize("hasAuthority('read:report:all')")
    @Operation(summary = "Get count summary", description = "Fetch summary of user data for frequency count within time range.")
    @GetMapping("/users/summary/count")
    fun getUserSummaryCount(filter: Analytics.Query.UserFilter): ResponseEntity<ApiResponse<Analytics.Report.Summary>> {
        require(!filter.from.isAfter(filter.to)) { "'from' must not be after 'to'" }

        val report = userService.getCountSummary(filter)
        return report.toResponseEntity(HttpStatus.OK)
    }
}
