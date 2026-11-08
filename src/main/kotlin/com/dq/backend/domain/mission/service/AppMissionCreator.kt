package com.dq.backend.domain.mission.service

import com.dq.backend.domain.mission.entity.Mission
import com.dq.backend.domain.mission.entity.MissionType
import com.dq.backend.domain.mission.entity.RewardType
import com.dq.backend.domain.mission.repository.MissionRepository
import com.dq.backend.domain.mission.repository.MissionTemplateRepository
import com.dq.backend.global.common.TimeUtils
import com.dq.backend.global.error.BusinessException
import com.dq.backend.global.error.ErrorCode
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.LocalDate

/**
 * 오늘의 APP 미션 1건을 **새 트랜잭션**에서 만든다.
 * AppMissionService 와 분리한 이유: 같은 빈 안에서 자기 메서드를 부르면 트랜잭션 프록시가 적용되지 않아
 * 호출자의 읽기 전용 트랜잭션 안에서 INSERT 가 실행되고(PostgreSQL 에서 실패), 유니크 충돌 시 호출자 트랜잭션이 rollback-only 가 된다.
 */
@Component
class AppMissionCreator(
    private val missionRepository: MissionRepository,
    private val templateRepository: MissionTemplateRepository,
) {
    companion object {
        /** 같은 룸에서 최근 N일 내 나온 템플릿 제외 (api.md 12장 #8 기본안) */
        const val EXCLUDE_RECENT_DAYS = 14L
    }

    private val random = SecureRandom()

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun createToday(roomId: Long, today: LocalDate): Mission {
        val template = pickTemplate(roomId, today)
        return missionRepository.save(
            Mission(
                roomId = roomId,
                type = MissionType.APP,
                content = template.content,
                rewardType = RewardType.ITEM,
                dueAt = TimeUtils.endOfDayKst(today),
                templateId = template.id,
                rewardItemId = template.rewardItemId,
                date = today,
            ),
        )
    }

    private fun pickTemplate(roomId: Long, today: LocalDate) =
        templateRepository.findAllByActiveTrue().ifEmpty { throw BusinessException(ErrorCode.NO_ACTIVE_TEMPLATE) }
            .let { all ->
                val recent = missionRepository.findRecentTemplateIds(roomId, today.minusDays(EXCLUDE_RECENT_DAYS))
                val candidates = all.filter { it.id !in recent }.ifEmpty { all } // 전부 제외되면 전체에서 재선정 (12장 #9)
                candidates[random.nextInt(candidates.size)]
            }
}
