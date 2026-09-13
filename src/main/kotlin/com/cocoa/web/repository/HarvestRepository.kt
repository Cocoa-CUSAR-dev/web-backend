package com.cocoa.web.repository

import com.cocoa.generated.agriculture.Tables.FARM
import com.cocoa.generated.collection.Tables.HARVEST
import com.cocoa.generated.collection.Tables.HARVEST_GRADE_DETAIL
import com.cocoa.web.base.BaseRepository
import com.cocoa.web.base.PageRequest
import com.cocoa.web.model.Analytics
import com.cocoa.web.util.dateTrunc
import com.cocoa.web.util.withDateRange
import org.jooq.AggregateFunction
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.UUID

@Repository
class HarvestRepository(
    dsl: DSLContext,
) : BaseRepository(dsl) {
    private val geoTable = DSL.table(DSL.name("storage", "geo"))
    private val geoId = DSL.field(DSL.name("storage", "geo", "geo_id"), UUID::class.java)
    private val geoGeom = DSL.field(DSL.name("storage", "geo", "geom"))

    data class MonthlyGradeRow(
        val month: LocalDate,
        val gradeCode: String,
        val value: Double,
    )

    data class GradeValueRow(
        val gradeCode: String,
        val value: Double,
    )

    // Location based
    fun fetchMonthlyDelta(filter: Analytics.Query.HarvestFilter): List<MonthlyGradeRow> {
        return fetchMonthlyMetric(filter, DSL.sum(HARVEST_GRADE_DETAIL.QUANTITY_KG))
    }

    fun fetchMonthlyAvg(filter: Analytics.Query.HarvestFilter): List<MonthlyGradeRow> {
        return fetchMonthlyMetric(filter, DSL.avg(HARVEST_GRADE_DETAIL.QUANTITY_KG))
    }

    fun fetchMonthlyFreq(filter: Analytics.Query.HarvestFilter): List<MonthlyGradeRow> {
        return fetchMonthlyMetric(filter, DSL.count())
    }

    fun fetchTotalQuantity(filter: Analytics.Query.HarvestFilter): Double {
        return fetchScalarMetric(filter, DSL.sum(HARVEST_GRADE_DETAIL.QUANTITY_KG)).toDouble()
    }

    fun fetchTotalCount(filter: Analytics.Query.HarvestFilter): Long {
        return fetchScalarMetric(filter, DSL.count()).toLong()
    }

    // Spatial based
    fun fetchSpatialMonthlyDelta(filter: Analytics.Query.SpatialHarvestFilter): List<MonthlyGradeRow> {
        return fetchSpatialMonthlyMetric(filter, DSL.sum(HARVEST_GRADE_DETAIL.QUANTITY_KG))
    }

    fun fetchSpatialMonthlyAvg(filter: Analytics.Query.SpatialHarvestFilter): List<MonthlyGradeRow> {
        return fetchSpatialMonthlyMetric(filter, DSL.avg(HARVEST_GRADE_DETAIL.QUANTITY_KG))
    }

    fun fetchSpatialMonthlyFreq(filter: Analytics.Query.SpatialHarvestFilter): List<MonthlyGradeRow> {
        return fetchSpatialMonthlyMetric(filter, DSL.count())
    }

    fun fetchSpatialTotalQuantity(filter: Analytics.Query.SpatialHarvestFilter): Double {
        return fetchSpatialScalarMetric(filter, DSL.sum(HARVEST_GRADE_DETAIL.QUANTITY_KG)).toDouble()
    }

    fun fetchSpatialTotalCount(filter: Analytics.Query.SpatialHarvestFilter): Long {
        return fetchSpatialScalarMetric(filter, DSL.count()).toLong()
    }

    // BE-9: same fix as FarmRepository.fetchFarmExportData -- paged instead
    // of a single unbounded .fetch(), with harvest_id added to the order so
    // rows sharing a harvest_date don't reshuffle across pages.
    fun fetchHarvestExportData(pageRequest: PageRequest): List<Record> {
        return dsl.select(
            HARVEST.HARVEST_DATE,
            FARM.FARM_NAME,
            HARVEST_GRADE_DETAIL.GRADE_CODE,
            HARVEST_GRADE_DETAIL.QUANTITY_KG,
            HARVEST_GRADE_DETAIL.WEIGHT_GRAM_PER_POD,
            HARVEST_GRADE_DETAIL.IS_CLEAN,
            HARVEST_GRADE_DETAIL.IS_APPROPRIATE_SIZE,
            HARVEST_GRADE_DETAIL.IS_SPROUT,
            HARVEST_GRADE_DETAIL.IS_DRY,
            HARVEST_GRADE_DETAIL.IS_SHRIVELED,
            HARVEST_GRADE_DETAIL.CUT_TEST_RESULT,
        )
            .from(HARVEST)
            .join(HARVEST_GRADE_DETAIL).on(HARVEST_GRADE_DETAIL.HARVEST_ID.eq(HARVEST.HARVEST_ID))
            .join(FARM).on(FARM.FARM_ID.eq(HARVEST.FARM_ID))
            .orderBy(HARVEST.HARVEST_DATE.desc(), HARVEST.HARVEST_ID)
            .limit(pageRequest.size)
            .offset(pageRequest.offset)
            .fetch()
            .map { it as Record }
    }

    // Helper Functions

    // Monthly
    private fun <T : Number> fetchMonthlyMetric(
        filter: Analytics.Query.HarvestFilter,
        metricField: AggregateFunction<T>,
    ): List<MonthlyGradeRow> {
        val monthBucket = HARVEST.HARVEST_DATE.dateTrunc("month")
        var conditions = DSL.noCondition().withDateRange(filter, HARVEST.HARVEST_DATE)

        filter.provinceId?.let { conditions = conditions.and(FARM.PROVINCE_ID.eq(it)) }
        filter.districtId?.let { conditions = conditions.and(FARM.DISTRICT_ID.eq(it)) }
        filter.subdistrictId?.let { conditions = conditions.and(FARM.SUBDISTRICT_ID.eq(it)) }
        filter.farmId?.let { conditions = conditions.and(HARVEST.FARM_ID.eq(it)) }
        filter.gradeCode?.let { conditions = conditions.and(HARVEST_GRADE_DETAIL.GRADE_CODE.eq(it)) }

        return dsl.select(
            monthBucket.`as`("month"),
            HARVEST_GRADE_DETAIL.GRADE_CODE,
            metricField.`as`("value"),
        )
            .from(HARVEST)
            .join(HARVEST_GRADE_DETAIL).on(HARVEST_GRADE_DETAIL.HARVEST_ID.eq(HARVEST.HARVEST_ID))
            .join(FARM).on(FARM.FARM_ID.eq(HARVEST.FARM_ID))
            .where(conditions)
            .groupBy(monthBucket, HARVEST_GRADE_DETAIL.GRADE_CODE)
            .orderBy(monthBucket.asc())
            .fetch { r ->
                MonthlyGradeRow(
                    month = r.get("month", LocalDate::class.java),
                    gradeCode = r.get(HARVEST_GRADE_DETAIL.GRADE_CODE),
                    value = r.get("value", Number::class.java).toDouble(),
                )
            }
    }

    private fun <T : Number> fetchSpatialMonthlyMetric(
        filter: Analytics.Query.SpatialHarvestFilter,
        metricField: AggregateFunction<T>,
    ): List<MonthlyGradeRow> {
        val monthBucket = HARVEST.HARVEST_DATE.dateTrunc("month")
        var conditions = DSL.noCondition().withDateRange(filter, HARVEST.HARVEST_DATE)

        filter.gradeCode?.let { conditions = conditions.and(HARVEST_GRADE_DETAIL.GRADE_CODE.eq(it)) }
        filter.geoJson?.let {
            conditions =
                conditions.and(
                    DSL.condition(
                        "ST_Intersects({0}, ST_SetSRID(ST_GeomFromGeoJSON({1}), 4326))",
                        geoGeom, it,
                    ),
                )
        }

        return dsl.select(
            monthBucket.`as`("month"),
            HARVEST_GRADE_DETAIL.GRADE_CODE,
            metricField.`as`("value"),
        )
            .from(HARVEST)
            .join(HARVEST_GRADE_DETAIL).on(HARVEST_GRADE_DETAIL.HARVEST_ID.eq(HARVEST.HARVEST_ID))
            .join(FARM).on(FARM.FARM_ID.eq(HARVEST.FARM_ID))
            .join(geoTable).on(geoId.eq(FARM.GEO_ID))
            .where(conditions)
            .groupBy(monthBucket, HARVEST_GRADE_DETAIL.GRADE_CODE)
            .orderBy(monthBucket.asc())
            .fetch { r ->
                MonthlyGradeRow(
                    month = r.get("month", LocalDate::class.java),
                    gradeCode = r.get(HARVEST_GRADE_DETAIL.GRADE_CODE),
                    value = r.get("value", Number::class.java).toDouble(),
                )
            }
    }

    // scalar
    private fun <T : Number> fetchScalarMetric(
        filter: Analytics.Query.HarvestFilter,
        metricField: AggregateFunction<T>,
    ): Number {
        var conditions = DSL.noCondition().withDateRange(filter, HARVEST.HARVEST_DATE)

        filter.provinceId?.let { conditions = conditions.and(FARM.PROVINCE_ID.eq(it)) }
        filter.districtId?.let { conditions = conditions.and(FARM.DISTRICT_ID.eq(it)) }
        filter.subdistrictId?.let { conditions = conditions.and(FARM.SUBDISTRICT_ID.eq(it)) }
        filter.farmId?.let { conditions = conditions.and(HARVEST.FARM_ID.eq(it)) }
        filter.gradeCode?.let { conditions = conditions.and(HARVEST_GRADE_DETAIL.GRADE_CODE.eq(it)) }

        val record =
            dsl.select(metricField)
                .from(HARVEST)
                .join(HARVEST_GRADE_DETAIL).on(HARVEST_GRADE_DETAIL.HARVEST_ID.eq(HARVEST.HARVEST_ID))
                .join(FARM).on(FARM.FARM_ID.eq(HARVEST.FARM_ID))
                .where(conditions)
                .fetchOne()

        return (record?.get(0) as? Number) ?: 0
    }

    private fun <T : Number> fetchSpatialScalarMetric(
        filter: Analytics.Query.SpatialHarvestFilter,
        metricField: AggregateFunction<T>,
    ): Number {
        var conditions = DSL.noCondition().withDateRange(filter, HARVEST.HARVEST_DATE)

        filter.gradeCode?.let { conditions = conditions.and(HARVEST_GRADE_DETAIL.GRADE_CODE.eq(it)) }
        filter.geoJson?.let {
            conditions =
                conditions.and(
                    DSL.condition(
                        "ST_Intersects({0}, ST_SetSRID(ST_GeomFromGeoJSON({1}), 4326))",
                        geoGeom, it,
                    ),
                )
        }

        val record =
            dsl.select(metricField)
                .from(HARVEST)
                .join(HARVEST_GRADE_DETAIL).on(HARVEST_GRADE_DETAIL.HARVEST_ID.eq(HARVEST.HARVEST_ID))
                .join(FARM).on(FARM.FARM_ID.eq(HARVEST.FARM_ID))
                .join(geoTable).on(geoId.eq(FARM.GEO_ID))
                .where(conditions)
                .fetchOne()

        return (record?.get(0) as? Number) ?: 0
    }
}
