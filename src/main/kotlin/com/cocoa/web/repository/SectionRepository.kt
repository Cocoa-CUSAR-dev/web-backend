package com.cocoa.web.repository

import com.cocoa.generated.form.Tables.SECTION
import com.cocoa.web.base.BaseRepository
import com.cocoa.web.model.Section
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class SectionRepository(
    dsl: DSLContext,
) : BaseRepository(dsl) {

    fun batchUpdate(sections: List<Section.Request.Edit>) {
        dsl.batched { ctx ->
            sections.forEach { section ->
                ctx.dsl().update(SECTION)
                    .set(SECTION.IS_ACTIVE, section.isActive)
                    .where(SECTION.SECTION_ID.eq(section.sectionId))
                    .execute()
            }
        }
    }

}
