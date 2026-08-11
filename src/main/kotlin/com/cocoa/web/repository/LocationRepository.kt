package com.cocoa.web.repository

import com.cocoa.generated.agriculture.Tables.FARM
import com.cocoa.generated.ref.Tables.DISTRICT_CONSTANT
import com.cocoa.generated.ref.Tables.SUBDISTRICT_CONSTANT
import com.cocoa.web.base.BaseRepository
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class LocationRepository(dsl: DSLContext) : BaseRepository(dsl) {
    fun isHierarchyValid(
        provinceId: UUID?,
        districtId: UUID?,
        subdistrictId: UUID?,
        farmId: UUID?,
    ): Boolean {
        return when {
            farmId != null -> validateFarmHierarchy(farmId, subdistrictId, districtId, provinceId)
            subdistrictId != null -> validateSubdistrictHierarchy(subdistrictId, districtId, provinceId)
            districtId != null && provinceId != null -> validateDistrictToProvinceLink(districtId, provinceId)
            else -> true
        }
    }

    fun enrichHierarchy(
        provinceId: UUID?,
        districtId: UUID?,
        subdistrictId: UUID?,
        farmId: UUID?,
    ): Map<String, Any?> {
        val metadata =
            mutableMapOf<String, Any?>(
                "provinceId" to provinceId,
                "districtId" to districtId,
                "subdistrictId" to subdistrictId,
                "farmId" to farmId,
            )

        when {
            metadata["farmId"] != null -> {
                fillHierarchyFromFarm(metadata)
            }
            metadata["subdistrictId"] != null -> {
                fillHierarchyFromSubdistrict(metadata)
            }
            metadata["districtId"] != null -> {
                fillHierarchyFromDistrict(metadata)
            }
        }

        return metadata
    }

    // Helper Functions
    private fun validateFarmHierarchy(
        farmId: UUID,
        providedSubdistrictId: UUID?,
        providedDistrictId: UUID?,
        providedProvinceId: UUID?,
    ): Boolean {
        val actualFarmLocation =
            dsl.select(
                FARM.SUBDISTRICT_ID,
                FARM.DISTRICT_ID,
                FARM.PROVINCE_ID,
            )
                .from(FARM)
                .where(FARM.FARM_ID.eq(farmId))
                .fetchOne() ?: return false

        val actualSubdistrictId = actualFarmLocation.get(FARM.SUBDISTRICT_ID)
        val actualDistrictId = actualFarmLocation.get(FARM.DISTRICT_ID)
        val actualProvinceId = actualFarmLocation.get(FARM.PROVINCE_ID)

        if (providedSubdistrictId != null && providedSubdistrictId != actualSubdistrictId) return false
        if (providedDistrictId != null && providedDistrictId != actualDistrictId) return false
        if (providedProvinceId != null && providedProvinceId != actualProvinceId) return false

        return true
    }

    private fun validateSubdistrictHierarchy(
        subdistrictId: UUID,
        providedDistrictId: UUID?,
        providedProvinceId: UUID?,
    ): Boolean {
        val actualHierarchy =
            dsl.select(
                SUBDISTRICT_CONSTANT.DISTRICT_ID,
                DISTRICT_CONSTANT.PROVINCE_ID,
            )
                .from(SUBDISTRICT_CONSTANT)
                .join(DISTRICT_CONSTANT).on(DISTRICT_CONSTANT.DISTRICT_ID.eq(SUBDISTRICT_CONSTANT.DISTRICT_ID))
                .where(SUBDISTRICT_CONSTANT.SUBDISTRICT_ID.eq(subdistrictId))
                .fetchOne() ?: return false

        val actualDistrictId = actualHierarchy.get(SUBDISTRICT_CONSTANT.DISTRICT_ID)
        val actualProvinceId = actualHierarchy.get(DISTRICT_CONSTANT.PROVINCE_ID)

        if (providedDistrictId != null && providedDistrictId != actualDistrictId) return false
        if (providedProvinceId != null && providedProvinceId != actualProvinceId) return false

        return true
    }

    private fun validateDistrictToProvinceLink(
        districtId: UUID,
        provinceId: UUID,
    ): Boolean {
        return dsl.fetchExists(
            dsl.selectOne()
                .from(DISTRICT_CONSTANT)
                .where(DISTRICT_CONSTANT.DISTRICT_ID.eq(districtId))
                .and(DISTRICT_CONSTANT.PROVINCE_ID.eq(provinceId)),
        )
    }

    private fun fillHierarchyFromFarm(metadata: MutableMap<String, Any?>) {
        val farmId = metadata["farmId"] as UUID
        val record =
            dsl.select(FARM.PROVINCE_ID, FARM.DISTRICT_ID, FARM.SUBDISTRICT_ID)
                .from(FARM)
                .where(FARM.FARM_ID.eq(farmId))
                .fetchOne()

        metadata["provinceId"] = record?.get(FARM.PROVINCE_ID)
        metadata["districtId"] = record?.get(FARM.DISTRICT_ID)
        metadata["subdistrictId"] = record?.get(FARM.SUBDISTRICT_ID)
    }

    private fun fillHierarchyFromSubdistrict(metadata: MutableMap<String, Any?>) {
        val subdistrictId = metadata["subdistrictId"] as UUID
        val record =
            dsl.select(SUBDISTRICT_CONSTANT.DISTRICT_ID, DISTRICT_CONSTANT.PROVINCE_ID)
                .from(SUBDISTRICT_CONSTANT)
                .join(DISTRICT_CONSTANT).on(DISTRICT_CONSTANT.DISTRICT_ID.eq(SUBDISTRICT_CONSTANT.DISTRICT_ID))
                .where(SUBDISTRICT_CONSTANT.SUBDISTRICT_ID.eq(subdistrictId))
                .fetchOne()

        metadata["districtId"] = record?.get(SUBDISTRICT_CONSTANT.DISTRICT_ID)
        metadata["provinceId"] = record?.get(DISTRICT_CONSTANT.PROVINCE_ID)
    }

    private fun fillHierarchyFromDistrict(metadata: MutableMap<String, Any?>) {
        val districtId = metadata["districtId"] as UUID
        val provinceId =
            dsl.select(DISTRICT_CONSTANT.PROVINCE_ID)
                .from(DISTRICT_CONSTANT)
                .where(DISTRICT_CONSTANT.DISTRICT_ID.eq(districtId))
                .fetchOneInto(UUID::class.java)

        metadata["provinceId"] = provinceId
    }
}
