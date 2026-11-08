package com.dq.backend.domain.mission.service

import com.dq.backend.domain.mission.entity.Mission
import com.dq.backend.domain.mission.entity.MissionType
import com.dq.backend.domain.mission.repository.MissionRepository
import com.dq.backend.domain.room.repository.RoomRepository
import com.dq.backend.global.common.TimeUtils
import com.dq.backend.global.error.BusinessException
import com.dq.backend.global.error.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

/**
 * 오늘의 APP 미션 조회·생성 (api.md 5장 "APP 미션 생성 규칙").
 * - 매일 KST 00:00 스케줄러가 모든 룸에 대해 생성
 * - 조회 시점에 없으면 lazy 생성
 * - (room_id, date) 유니크 인덱스로 경합 시에도 하루 1개 보장
 */
@Service
class AppMissionService(
    private val missionRepository: MissionRepository,
    private val roomRepository: RoomRepository,
    private val creator: AppMissionCreator,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 오늘의 APP 미션. 없으면 만들어서 돌려준다. 호출자의 트랜잭션(읽기 전용이어도)과 무관하게 동작한다. */
    fun getOrCreateToday(roomId: Long): Mission {
        val today = TimeUtils.todayKst()
        missionRepository.findByRoomIdAndTypeAndDate(roomId, MissionType.APP, today)?.let { return it }
        return try {
            creator.createToday(roomId, today)
        } catch (e: DataIntegrityViolationException) {
            // 스케줄러나 다른 요청이 먼저 만들었음 → 재조회
            missionRepository.findByRoomIdAndTypeAndDate(roomId, MissionType.APP, today) ?: throw e
        }
    }

    /** 룸 상세 등 부가 정보용: 템플릿이 하나도 없으면 null (api.md 4.4 todayMission). */
    fun getOrCreateTodayOrNull(roomId: Long): Mission? = try {
        getOrCreateToday(roomId)
    } catch (e: BusinessException) {
        if (e.errorCode == ErrorCode.NO_ACTIVE_TEMPLATE) null else throw e
    }

    /** 매일 KST 00:00 모든 룸의 오늘 미션 생성 */
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    fun createTodayForAllRooms() {
        val roomIds = roomRepository.findAll().map { it.id }
        var created = 0
        roomIds.forEach { roomId ->
            runCatching { getOrCreateToday(roomId); created++ }
                .onFailure { log.warn("오늘의 APP 미션 생성 실패 roomId={}", roomId, it) }
        }
        log.info("오늘의 APP 미션 생성 완료: {}/{} 룸", created, roomIds.size)
    }
}
