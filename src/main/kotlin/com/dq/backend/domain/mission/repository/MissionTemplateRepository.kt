package com.dq.backend.domain.mission.repository

import com.dq.backend.domain.mission.entity.MissionTemplate
import org.springframework.data.jpa.repository.JpaRepository

interface MissionTemplateRepository : JpaRepository<MissionTemplate, Long> {
    fun findAllByActiveTrue(): List<MissionTemplate>
}
