package com.cocoa.web.controller

import com.cocoa.web.base.BaseController
import com.cocoa.web.model.Analytics
import com.cocoa.web.model.ApiResponse
import com.cocoa.web.model.toResponseEntity
import com.cocoa.web.service.SpatialHarvestAnalyticsService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/analytics/harvest/spatial")
@Tag(name = "Spatial Analytics", description = "Map-based time-series reports using GeoJSON boundaries")
class SpatialAnalyticsController(
    private val spatialService: SpatialHarvestAnalyticsService,
) : BaseController() {
    // --- Time Series Endpoints ---

    @PreAuthorize("hasAuthority('read:report:all')")
    @Operation(summary = "Get spatial delta series", description = "Fetch monthly harvest data for a map-drawn area.")
    @PostMapping("/time-series/delta")
    fun getSpatialDeltaSeries(
        query: Analytics.Query.SpatialHarvestQuery,
        @RequestBody payload: Analytics.Query.SpatialPayload,
    ): ResponseEntity<ApiResponse<Analytics.Report.TimeSeries>> {
        val filter = buildSpatialHarvestFilter(query, payload)
        val report = spatialService.getSpatialDeltaSeries(filter)
        return report.toResponseEntity(HttpStatus.OK)
    }

    @PreAuthorize("hasAuthority('read:report:all')")
    @Operation(summary = "Get spatial cumulative series", description = "Fetch accumulating harvest data for a map-drawn area.")
    @PostMapping("/time-series/sum")
    fun getSpatialCumulativeSeries(
        query: Analytics.Query.SpatialHarvestQuery,
        @RequestBody payload: Analytics.Query.SpatialPayload,
    ): ResponseEntity<ApiResponse<Analytics.Report.TimeSeries>> {
        val filter = buildSpatialHarvestFilter(query, payload)
        val report = spatialService.getSpatialCumulativeSeries(filter)
        return report.toResponseEntity(HttpStatus.OK)
    }

    @PreAuthorize("hasAuthority('read:report:all')")
    @Operation(summary = "Get spatial average series", description = "Fetch monthly average weight for a map-drawn area.")
    @PostMapping("/time-series/average")
    fun getSpatialAverageSeries(
        query: Analytics.Query.SpatialHarvestQuery,
        @RequestBody payload: Analytics.Query.SpatialPayload,
    ): ResponseEntity<ApiResponse<Analytics.Report.TimeSeries>> {
        val filter = buildSpatialHarvestFilter(query, payload)
        val report = spatialService.getSpatialAverageSeries(filter)
        return report.toResponseEntity(HttpStatus.OK)
    }

    @PreAuthorize("hasAuthority('read:report:all')")
    @Operation(summary = "Get spatial frequency series", description = "Fetch monthly event counts for a map-drawn area.")
    @PostMapping("/time-series/frequency")
    fun getSpatialFrequencySeries(
        query: Analytics.Query.SpatialHarvestQuery,
        @RequestBody payload: Analytics.Query.SpatialPayload,
    ): ResponseEntity<ApiResponse<Analytics.Report.TimeSeries>> {
        val filter = buildSpatialHarvestFilter(query, payload)
        val report = spatialService.getSpatialFrequencySeries(filter)
        return report.toResponseEntity(HttpStatus.OK)
    }

    // --- Summary Endpoints ---

    @PreAuthorize("hasAuthority('read:report:all')")
    @Operation(summary = "Get spatial total weight", description = "Fetch total harvest weight for a map-drawn area.")
    @PostMapping("/summary/total")
    fun getSpatialTotalSummary(
        query: Analytics.Query.SpatialHarvestQuery,
        @RequestBody payload: Analytics.Query.SpatialPayload,
    ): ResponseEntity<ApiResponse<Analytics.Report.Summary>> {
        val filter = buildSpatialHarvestFilter(query, payload)
        val report = spatialService.getSpatialTotalSummary(filter)
        return report.toResponseEntity(HttpStatus.OK)
    }

    @PreAuthorize("hasAuthority('read:report:all')")
    @Operation(summary = "Get spatial event count", description = "Fetch total number of harvest events for a map-drawn area.")
    @PostMapping("/summary/count")
    fun getSpatialFrequencySummary(
        query: Analytics.Query.SpatialHarvestQuery,
        @RequestBody payload: Analytics.Query.SpatialPayload,
    ): ResponseEntity<ApiResponse<Analytics.Report.Summary>> {
        val filter = buildSpatialHarvestFilter(query, payload)
        val report = spatialService.getSpatialFrequencySummary(filter)
        return report.toResponseEntity(HttpStatus.OK)
    }

    // Helper Functions
    private fun buildSpatialHarvestFilter(
        harvestQuery: Analytics.Query.SpatialHarvestQuery,
        spatialPayload: Analytics.Query.SpatialPayload,
    ): Analytics.Query.SpatialHarvestFilter {
        require(!harvestQuery.from.isAfter(harvestQuery.to)) { "'from' must not be after 'to'" }

        return Analytics.Query.SpatialHarvestFilter(
            from = harvestQuery.from,
            to = harvestQuery.to,
            gradeCode = harvestQuery.gradeCode,
            geoJson = spatialPayload.geoJson,
        )
    }
}
