package com.dq.backend.domain.mission.service

import com.dq.backend.domain.mission.dto.CreateMemberMissionRequest
import com.dq.backend.domain.mission.dto.MissionDetailResponse
import com.dq.backend.domain.mission.dto.MissionResponse
import com.dq.backend.domain.mission.dto.MyMissionsByRoomResponse
import com.dq.backend.domain.mission.dto.RoomBrief
import com.dq.backend.domain.mission.dto.UpdateMemberMissionRequest
import com.dq.backend.domain.mission.entity.Mission
import com.dq.backend.domain.mission.entity.MissionType
import com.dq.backend.domain.mission.entity.RewardType
import com.dq.backend.domain.mission.repository.MissionRepository
import com.dq.backend.domain.room.dto.UserBrief
import com.dq.backend.domain.room.repository.RoomMemberRepository
import com.dq.backend.domain.room.repository.RoomRepository
import com.dq.backend.domain.user.repository.UserRepository
import com.dq.backend.global.common.PageResponse
import com.dq.backend.global.error.BusinessException
import com.dq.backend.global.error.ErrorCode
import com.dq.backend.global.error.ForbiddenException
import com.dq.backend.global.error.NotFoundException
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

@Service
@Transactional(readOnly = true)
class MissionService(
    private val missionRepository: MissionRepository,
    private val roomRepository: RoomRepository,
    private val roomMemberRepository: RoomMemberRepository,
    private val userRepository: UserRepository,
    private val appMissionService: AppMissionService,
    private val proofStats: MissionProofStatsPort,
) {
    companion object {
        const val MAX_PAGE_SIZE = 100
        val DEFAULT_MEMBER_MISSION_DURATION: Duration = Duration.ofDays(7) // 12장 #11 기본안
    }

    /** 5.1 룸 미션 목록. status: ACTIVE(기본) | EXPIRED | ALL, type: APP | MEMBER | 생략 */
    fun listByRoom(userId: Long, roomId: Long, status: String?, type: String?, page: Int, size: Int): PageResponse<MissionResponse> {
        requireRoom(roomId)
        requireMember(roomId, userId)
        val activeFlag = when (status?.uppercase()) {
            null, "", "ACTIVE" -> true
            "EXPIRED" -> false
            "ALL" -> null
            else -> throw BusinessException(ErrorCode.VALIDATION_ERROR, "status는 ACTIVE, EXPIRED, ALL 중 하나여야 합니다.")
        }
        val missionType = type?.takeIf { it.isNotBlank() }?.let {
            runCatching { MissionType.valueOf(it.uppercase()) }
                .getOrElse { throw BusinessException(ErrorCode.VALIDATION_ERROR, "type은 APP 또는 MEMBER여야 합니다.") }
        }
        if (activeFlag != false && missionType != MissionType.MEMBER) appMissionService.getOrCreateToday(roomId) // 오늘 미션 보장
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, MAX_PAGE_SIZE))
        val result = missionRepository.findByRoom(roomId, missionType, activeFlag, Instant.now(), pageable)
        return PageResponse(
            content = toResponses(userId, result.content),
            page = result.number, size = result.size, totalElements = result.totalElements, totalPages = result.totalPages,
        )
    }

    /** 5.2 오늘의 APP 미션 (없으면 생성) */
    fun today(userId: Long, roomId: Long): MissionResponse {
        requireRoom(roomId)
        requireMember(roomId, userId)
        val mission = appMissionService.getOrCreateToday(roomId)
        return toResponses(userId, listOf(mission)).first()
    }

    /** 5.3 멤버 미션 생성 */
    @Transactional
    fun createMemberMission(userId: Long, roomId: Long, request: CreateMemberMissionRequest): MissionResponse {
        requireRoom(roomId)
        requireMember(roomId, userId)
        if (request.rewardCoins % 10 != 0) throw BusinessException(ErrorCode.VALIDATION_ERROR, "rewardCoins는 10단위여야 합니다.")
        val now = Instant.now()
        val dueAt = request.dueAt ?: now.plus(DEFAULT_MEMBER_MISSION_DURATION)
        if (!dueAt.isAfter(now)) throw BusinessException(ErrorCode.VALIDATION_ERROR, "dueAt은 미래 시각이어야 합니다.")
        val mission = missionRepository.save(
            Mission(
                roomId = roomId,
                type = MissionType.MEMBER,
                content = request.content.trim(),
                rewardType = RewardType.COIN,
                dueAt = dueAt,
                rewardCoins = request.rewardCoins,
                createdBy = userId,
            ),
        )
        return toResponses(userId, listOf(mission)).first()
    }

    /** 5.4 미션 상세 (멤버) */
    fun detail(userId: Long, missionId: Long): MissionDetailResponse {
        val mission = getMission(missionId)
        requireMember(mission.roomId, userId)
        return MissionDetailResponse.from(toResponses(userId, listOf(mission)).first(), proofStats.proofsOf(missionId))
    }

    /** 5.5 멤버 미션 수정 (작성자) */
    @Transactional
    fun update(userId: Long, missionId: Long, request: UpdateMemberMissionRequest): MissionResponse {
        val mission = getMission(missionId)
        if (mission.isApp()) throw BusinessException(ErrorCode.APP_MISSION_IMMUTABLE)
        if (mission.createdBy != userId) throw ForbiddenException()
        if (proofStats.hasAnyProof(missionId)) throw BusinessException(ErrorCode.MISSION_HAS_PROOFS)
        request.content?.let { mission.content = it.trim() }
        request.dueAt?.let {
            if (!it.isAfter(Instant.now())) throw BusinessException(ErrorCode.VALIDATION_ERROR, "dueAt은 미래 시각이어야 합니다.")
            mission.dueAt = it
        }
        return toResponses(userId, listOf(mission)).first()
    }

    /** 5.6 멤버 미션 삭제 (작성자 또는 방장) */
    @Transactional
    fun delete(userId: Long, missionId: Long) {
        val mission = getMission(missionId)
        if (mission.isApp()) throw BusinessException(ErrorCode.APP_MISSION_IMMUTABLE)
        val room = requireRoom(mission.roomId)
        if (mission.createdBy != userId && !room.isOwner(userId)) throw ForbiddenException()
        if (proofStats.hasAnyProof(missionId)) throw BusinessException(ErrorCode.MISSION_HAS_PROOFS)
        missionRepository.delete(mission)
    }

    /** 5.7 내 룸 전체의 진행 중 미션 (룸별 그룹) */
    fun mine(userId: Long): List<MyMissionsByRoomResponse> {
        val roomIds = roomRepository.findMine(userId).map { it.room.id }
        roomIds.forEach { appMissionService.getOrCreateToday(it) } // 오늘 미션 보장
        val missions = missionRepository.findActiveOfUser(userId, Instant.now())
        val responses = toResponses(userId, missions).associateBy { it.missionId }
        val rooms = roomRepository.findAllById(roomIds).associateBy { it.id }
        return missions.groupBy { it.roomId }.mapNotNull { (roomId, ms) ->
            rooms[roomId]?.let { room ->
                MyMissionsByRoomResponse(RoomBrief(room.id, room.name), ms.mapNotNull { responses[it.id] })
            }
        }
    }

    // ---------- 내부 ----------

    fun getMission(missionId: Long): Mission =
        missionRepository.findByIdOrNull(missionId) ?: throw NotFoundException(ErrorCode.MISSION_NOT_FOUND)

    private fun requireRoom(roomId: Long) =
        roomRepository.findByIdOrNull(roomId) ?: throw NotFoundException(ErrorCode.ROOM_NOT_FOUND)

    private fun requireMember(roomId: Long, userId: Long) {
        if (!roomMemberRepository.existsByIdRoomIdAndIdUserId(roomId, userId)) throw ForbiddenException()
    }

    private fun toResponses(userId: Long, missions: List<Mission>): List<MissionResponse> {
        if (missions.isEmpty()) return emptyList()
        val ids = missions.map { it.id }
        val counts = proofStats.proofCounts(ids)
        val mine = proofStats.myProofs(userId, ids)
        val creators = userRepository.findAllById(missions.mapNotNull { it.createdBy }.distinct()).associateBy { it.id }
        return missions.map { m ->
            MissionResponse.from(
                m,
                createdBy = m.createdBy?.let { creators[it] }?.let { UserBrief.from(it) },
                myProof = mine[m.id],
                proofCount = counts[m.id] ?: 0,
            )
        }
    }
}
