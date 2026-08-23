package com.cocoa.web.service

import com.cocoa.generated.agriculture.Tables.FARM
import com.cocoa.generated.collection.Tables.HARVEST
import com.cocoa.generated.collection.Tables.HARVEST_GRADE_DETAIL
import com.cocoa.generated.ref.Tables.DISTRICT_CONSTANT
import com.cocoa.generated.ref.Tables.PROVINCE_CONSTANT
import com.cocoa.generated.ref.Tables.SUBDISTRICT_CONSTANT
import com.cocoa.web.base.BaseService
import com.cocoa.web.base.PageRequest
import com.cocoa.web.repository.FarmRepository
import com.cocoa.web.repository.HarvestRepository
import org.apache.poi.xssf.streaming.SXSSFWorkbook
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream

@Service
class XlsxService(
    private val farmRepository: FarmRepository,
    private val harvestRepository: HarvestRepository,
) : BaseService() {
    // BE-9: SXSSFWorkbook only keeps EXPORT_PAGE_SIZE rows per sheet in
    // memory at once (older rows are flushed to a temp file automatically),
    // vs. XSSFWorkbook holding the entire workbook in memory -- paired with
    // paging the repository queries below (rather than one giant .fetch()),
    // this bounds memory regardless of how many farms/harvests exist.
    private val exportPageSize = 1000

    fun buildXlsxFile(vararg sheetBuilders: SXSSFWorkbook.() -> Unit): ByteArray {
        // .use{} calls close(), which (unlike the older, now-deprecated
        // dispose()) both closes the workbook AND cleans up SXSSF's backing
        // temp files -- no separate dispose() call needed.
        return SXSSFWorkbook(exportPageSize).use { workbook ->
            sheetBuilders.forEach { sheetBuilder ->
                workbook.sheetBuilder()
            }

            ByteArrayOutputStream().use { out ->
                workbook.write(out)
                out.toByteArray()
            }
        }
    }

    fun farmSheetBuilder(): SXSSFWorkbook.() -> Unit =
        {
            val sheet = createSheet("Farm Registry")
            val headers =
                listOf("Farm Name", "Found Date", "Area", "Province", "District", "Sub-District", "Contact", "Phone", "Line", "Facebook")
            val headerRow = sheet.createRow(0)
            headers.forEachIndexed { i, text -> headerRow.createCell(i).setCellValue(text) }

            var rowIndex = 1
            var page = 0
            while (true) {
                val records = farmRepository.fetchFarmExportData(PageRequest(page, exportPageSize))
                if (records.isEmpty()) break

                records.forEach { rec ->
                    val row = sheet.createRow(rowIndex++)

                    row.createCell(0).setCellValue(rec.get(FARM.FARM_NAME))
                    row.createCell(1).setCellValue(rec.get(FARM.FOUND_DATE).toString())
                    row.createCell(2).setCellValue(rec.get(FARM.TOTAL_AREA)?.toDouble() ?: 0.0)
                    row.createCell(3).setCellValue(rec.get(PROVINCE_CONSTANT.PROVINCE_NAME_EN))
                    row.createCell(4).setCellValue(rec.get(DISTRICT_CONSTANT.DISTRICT_NAME_EN))
                    row.createCell(5).setCellValue(rec.get(SUBDISTRICT_CONSTANT.SUBDISTRICT_NAME_EN))
                    row.createCell(6).setCellValue(rec.get(FARM.CONTACT_NAME))
                    row.createCell(7).setCellValue(rec.get(FARM.PHONE_NUMBER))
                    row.createCell(8).setCellValue(rec.get(FARM.LINE) ?: "-")
                    row.createCell(9).setCellValue(rec.get(FARM.FACEBOOK) ?: "-")
                }

                page++
            }

            // Streaming sheets can't randomly re-visit already-flushed rows,
            // so autoSizeColumn (which needs the whole column in memory) is
            // dropped rather than silently doing nothing on flushed rows.
        }

    fun harvestSheetBuilder(): SXSSFWorkbook.() -> Unit =
        {
            val sheet = this.createSheet("Raw Harvest Data")
            val headers = listOf("Date", "Farm", "Grade", "Kg", "Gram/Pod", "Clean", "Size OK", "Sprout", "Dry", "Shriveled", "Cut Test")
            val headerRow = sheet.createRow(0)
            headers.forEachIndexed { i, text -> headerRow.createCell(i).setCellValue(text) }

            var rowIndex = 1
            var page = 0
            while (true) {
                val records = harvestRepository.fetchHarvestExportData(PageRequest(page, exportPageSize))
                if (records.isEmpty()) break

                records.forEach { rec ->
                    val row = sheet.createRow(rowIndex++)

                    row.createCell(0).setCellValue(rec.get(HARVEST.HARVEST_DATE).toString())
                    row.createCell(1).setCellValue(rec.get(FARM.FARM_NAME))
                    row.createCell(2).setCellValue(rec.get(HARVEST_GRADE_DETAIL.GRADE_CODE))
                    row.createCell(3).setCellValue(rec.get(HARVEST_GRADE_DETAIL.QUANTITY_KG).toDouble())
                    row.createCell(4).setCellValue(rec.get(HARVEST_GRADE_DETAIL.WEIGHT_GRAM_PER_POD).toDouble())

                    row.createCell(5).setCellValue(if (rec.get(HARVEST_GRADE_DETAIL.IS_CLEAN)) "Y" else "N")
                    row.createCell(6).setCellValue(if (rec.get(HARVEST_GRADE_DETAIL.IS_APPROPRIATE_SIZE)) "Y" else "N")
                    row.createCell(7).setCellValue(if (rec.get(HARVEST_GRADE_DETAIL.IS_SPROUT)) "Y" else "N")
                    row.createCell(8).setCellValue(if (rec.get(HARVEST_GRADE_DETAIL.IS_DRY)) "Y" else "N")
                    row.createCell(9).setCellValue(if (rec.get(HARVEST_GRADE_DETAIL.IS_SHRIVELED)) "Y" else "N")

                    row.createCell(10).setCellValue(rec.get(HARVEST_GRADE_DETAIL.CUT_TEST_RESULT) ?: "-")
                }

                page++
            }
        }
}
