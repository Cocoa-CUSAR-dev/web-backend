package com.cocoa.web.controller

import com.cocoa.web.base.BaseController
import com.cocoa.web.exception.UnsupportedSheetException
import com.cocoa.web.service.XlsxService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@RestController
@RequestMapping("/reports")
@Tag(name = "Reports", description = "Endpoints for generating and downloading data exports")
class ReportController(
    private val xlsxService: XlsxService,
): BaseController() {

    enum class SheetType {
        FARM,
        HARVEST;

        companion object {
            fun fromString(value: String) = entries.find { it.name.equals(value, ignoreCase = true) }
        }
    }

    @Operation(summary = "Download raw data as Excel", description = "Generates an .xlsx file containing the specified data sheets. If no sheets are provided, all available sheets are included.",)
    @GetMapping("/raw-data/download")
    fun downloadRawData(
        @RequestParam(name = "sheets", required = false)
        @Parameter(
            description = "Sheet types to include in the export",
            schema = Schema(implementation = SheetType::class)
        )
        requestedSheets: List<SheetType>?
    ): ResponseEntity<ByteArray> {
        val activeSheets = requestedSheets ?: SheetType.entries
        val builders = activeSheets.map { type ->
            when (type) {
                SheetType.FARM -> xlsxService.farmSheetBuilder()
                SheetType.HARVEST -> xlsxService.harvestSheetBuilder()
            }
        }

        if (builders.isEmpty()) {
            throw UnsupportedSheetException()
        }

        val fileName = "raw_data"
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
        val xlsxSheet = xlsxService.buildXlsxFile(*builders.toTypedArray())

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=${fileName}_${timestamp}.xlsx")
            .contentLength(xlsxSheet.size.toLong())
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(xlsxSheet)
    }

}