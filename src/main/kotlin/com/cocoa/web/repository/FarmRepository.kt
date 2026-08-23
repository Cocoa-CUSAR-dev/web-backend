package com.cocoa.web.repository

import com.cocoa.generated.agriculture.Tables.FARM
import com.cocoa.generated.ref.Tables.DISTRICT_CONSTANT
import com.cocoa.generated.ref.Tables.PROVINCE_CONSTANT
import com.cocoa.generated.ref.Tables.SUBDISTRICT_CONSTANT
import com.cocoa.web.base.BaseRepository
import com.cocoa.web.base.PageRequest
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository

@Repository
class FarmRepository(dsl: DSLContext) : BaseRepository(dsl) {
    // BE-9: was an unbounded .fetch() loading every farm row into memory
    // for the Excel export. XlsxService now pages through this instead of
    // calling it once -- the existing orderBy is what makes that safe
    // (same order every page, no skipped/duplicated rows).
    fun fetchFarmExportData(pageRequest: PageRequest): List<Record> {
        return dsl.select(
            FARM.FARM_NAME,
            FARM.FOUND_DATE,
            FARM.TOTAL_AREA,
            PROVINCE_CONSTANT.PROVINCE_NAME_EN,
            DISTRICT_CONSTANT.DISTRICT_NAME_EN,
            SUBDISTRICT_CONSTANT.SUBDISTRICT_NAME_EN,
            FARM.CONTACT_NAME,
            FARM.PHONE_NUMBER,
            FARM.LINE,
            FARM.FACEBOOK,
        )
            .from(FARM)
            .leftJoin(PROVINCE_CONSTANT).on(FARM.PROVINCE_ID.eq(PROVINCE_CONSTANT.PROVINCE_ID))
            .leftJoin(DISTRICT_CONSTANT).on(FARM.DISTRICT_ID.eq(DISTRICT_CONSTANT.DISTRICT_ID))
            .leftJoin(SUBDISTRICT_CONSTANT).on(FARM.SUBDISTRICT_ID.eq(SUBDISTRICT_CONSTANT.SUBDISTRICT_ID))
            .orderBy(FARM.FARM_NAME.asc(), FARM.FARM_ID)
            .limit(pageRequest.size)
            .offset(pageRequest.offset)
            .fetch()
            .map { it as Record }
    }
}
