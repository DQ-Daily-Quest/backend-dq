package com.dq.backend.domain.mission.repository

import com.dq.backend.domain.mission.entity.Mission
import com.dq.backend.domain.mission.entity.MissionType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.time.LocalDate

interface MissionRepository : JpaRepository<Mission, Long> {

    fun findByRoomIdAndTypeAndDate(roomId: Long, type: MissionType, date: LocalDate): Mission?

    /** 최근 N일 내 이 룸에서 쓰인 템플릿 ID (중복 제외용) */
    @Query("select m.templateId from Mission m where m.roomId = :roomId and m.type = 'APP' and m.date >= :since and m.templateId is not null")
    fun findRecentTemplateIds(roomId: Long, since: LocalDate): Set<Long>

    /**
     * 룸 미션 목록 (api.md 5.1).
     * activeFlag: true = 진행 중(dueAt > now), false = 마감, null = 전체. type: null = 전체.
     */
    @Query(
        """
        select m from Mission m
        where m.roomId = :roomId
          and (:type is null or m.type = :type)
          and (:activeFlag is null or (:activeFlag = true and m.dueAt > :now) or (:activeFlag = false and m.dueAt <= :now))
        order by m.dueAt asc, m.createdAt desc
        """,
    )
    fun findByRoom(roomId: Long, type: MissionType?, activeFlag: Boolean?, now: Instant, pageable: Pageable): Page<Mission>

    /** 내가 속한 모든 룸의 진행 중 미션 (api.md 5.7) */
    @Query(
        """
        select m from Mission m
        where m.dueAt > :now
          and m.roomId in (select rm.id.roomId from RoomMember rm where rm.id.userId = :userId)
        order by m.roomId asc, m.dueAt asc, m.createdAt desc
        """,
    )
    fun findActiveOfUser(userId: Long, now: Instant): List<Mission>
}
