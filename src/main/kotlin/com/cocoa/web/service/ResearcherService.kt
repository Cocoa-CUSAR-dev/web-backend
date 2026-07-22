package com.cocoa.web.service

import com.cocoa.web.base.BaseService
import com.cocoa.web.exception.EntityNotFoundException
import com.cocoa.web.model.Researcher
import com.cocoa.web.repository.ResearcherRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class ResearcherService(
    private val researcherRepository: ResearcherRepository,
) : BaseService() {

    fun addResearcher(
        userId: UUID,
        researcherInfo: Researcher.Input.Create,
    ) {
        researcherRepository.insertResearcher(userId, researcherInfo)
    }

    fun getResearcher(userId: UUID): Researcher.Entity {
        return researcherRepository
            .findByUserId(userId)
            ?: throw EntityNotFoundException("Researcher not found")
    }

    fun getResearchers(filter: Researcher.Query.Filter): List<Researcher.Entity> {
        return researcherRepository.fetchResearchers(filter)
    }

    fun updateResearcher(userId: UUID, researcherUpdate: Researcher.Input.Update): Int {
        return researcherRepository
            .patchResearcher(userId, researcherUpdate)
            .orThrowNotFound("Researcher")
    }

    fun deleteResearcher(userId: UUID): Int {
        return researcherRepository
            .deleteResearcher(userId)
            .orThrowNotFound("Researcher")
    }

}