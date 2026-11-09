package com.dq.backend.domain.room.service

import com.dq.backend.domain.room.dto.CreateRoomRequest
import com.dq.backend.domain.room.dto.CreateRoomResponse
import com.dq.backend.domain.room.dto.InviteCodeResponse
import com.dq.backend.domain.room.dto.RoomDetailResponse
import com.dq.backend.domain.room.dto.RoomMemberResponse
import com.dq.backend.domain.room.dto.RoomSummaryResponse
import com.dq.backend.domain.room.dto.UpdateRoomRequest
import com.dq.backend.domain.mission.service.AppMissionService
import com.dq.backend.domain.proof.repository.ProofRepository
import com.dq.backend.domain.room.dto.TodayMissionBrief
import com.dq.backend.domain.room.dto.UserBrief
import com.dq.backend.domain.room.entity.Room
import com.dq.backend.domain.room.entity.RoomMember
import com.dq.backend.domain.room.entity.RoomMemberId
import com.dq.backend.domain.room.entity.RoomRole
import com.dq.backend.domain.room.repository.RoomMemberRepository
import com.dq.backend.domain.room.repository.RoomRepository
import com.dq.backend.domain.room.repository.RoomWithCount
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
class RoomService(
    private val roomRepository: RoomRepository,
    private val roomMemberRepository: RoomMemberRepository,
    private val userRepository: UserRepository,
    private val inviteCodeGenerator: InviteCodeGenerator,
    private val appMissionService: AppMissionService,
    private val proofRepository: ProofRepository,
) {
    companion object {
        /** isHot: 인기 점수 상위 N개 (api.md 12장 #4 기본안) */
        const val HOT_ROOM_COUNT = 3
        const val MAX_PAGE_SIZE = 100
        val POPULARITY_WINDOW: Duration = Duration.ofDays(7)
    }

    private fun popularitySince() = Instant.now().minus(POPULARITY_WINDOW)

    // ---------- 조회 ----------

    /** 4.1 공개 룸 목록. sort: popularity(기본) | memberCount | createdAt */
    fun listPublic(userId: Long, keyword: String?, sort: String?, page: Int, size: Int): PageResponse<RoomSummaryResponse> {
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, MAX_PAGE_SIZE))
        val kw = keyword?.trim().orEmpty()
        val result = when (sort?.substringBefore(',')) {
            "createdAt" -> roomRepository.findPublicByCreatedAt(kw, pageable)
            else -> roomRepository.findPublicByPopularity(kw, popularitySince(), pageable)
        }
        val hotIds = roomRepository.findPublicByPopularity("", popularitySince(), PageRequest.of(0, HOT_ROOM_COUNT))
            .content.map { it.room.id }.toSet()
        val joinedIds = roomMemberRepository.findJoinedRoomIds(userId, result.content.map { it.room.id })
        return PageResponse.from(result) { toSummary(it, isHot = it.room.id in hotIds, joined = it.room.id in joinedIds) }
    }

    /** 4.2 내 룸 목록 */
    fun listMine(userId: Long): List<RoomSummaryResponse> {
        val hotIds = roomRepository.findPublicByPopularity("", popularitySince(), PageRequest.of(0, HOT_ROOM_COUNT))
            .content.map { it.room.id }.toSet()
        return roomRepository.findMine(userId).map {
            toSummary(it, isHot = it.room.id in hotIds, joined = true).copy(
                todayMissionDone = userId in todayDoneUserIds(it.room.id),
                pendingProofCount = proofRepository.countPendingForVoter(it.room.id, userId),
            )
        }
    }

    /** 4.4 룸 상세. 비공개 룸은 멤버만. */
    fun getDetail(userId: Long, roomId: Long): RoomDetailResponse {
        val room = getRoom(roomId)
        val membership = roomMemberRepository.findByIdRoomIdAndIdUserId(roomId, userId)
        if (!room.isPublic && membership == null) throw ForbiddenException()
        return toDetail(room, membership?.role)
    }

    /** 4.10 멤버 목록 (멤버만) */
    fun listMembers(userId: Long, roomId: Long): List<RoomMemberResponse> {
        getRoom(roomId)
        requireMember(roomId, userId)
        val members = roomMemberRepository.findAllByIdRoomIdOrderByJoinedAtAsc(roomId)
        val users = userRepository.findAllById(members.map { it.userId }).associateBy { it.id }
        val done = todayDoneUserIds(roomId)
        return members.mapNotNull { m -> users[m.userId]?.let { RoomMemberResponse.from(m, it, todayMissionDone = m.userId in done) } }
    }

    // ---------- 생성 / 수정 / 삭제 ----------

    /** 4.3 룸 생성. 생성자는 OWNER, 비공개면 초대코드 발급. */
    @Transactional
    fun create(userId: Long, request: CreateRoomRequest): CreateRoomResponse {
        val room = roomRepository.save(
            Room(
                name = request.name.trim(),
                description = request.description?.trim()?.ifBlank { null },
                isPublic = request.isPublic,
                ownerId = userId,
                maxMembers = request.maxMembers,
            ).apply { if (!request.isPublic) inviteCode = inviteCodeGenerator.generate() },
        )
        roomMemberRepository.save(RoomMember(RoomMemberId(room.id, userId), RoomRole.OWNER))
        return CreateRoomResponse.from(room)
    }

    /** 4.5 룸 수정 (방장). 공개↔비공개 전환 시 초대코드 발급/제거. */
    @Transactional
    fun update(userId: Long, roomId: Long, request: UpdateRoomRequest): RoomDetailResponse {
        val room = getRoom(roomId)
        requireOwner(room, userId)
        request.name?.let { room.name = it.trim() }
        request.description?.let { room.description = it.trim().ifBlank { null } }
        request.maxMembers?.let {
            if (it < roomMemberRepository.countByIdRoomId(roomId)) throw BusinessException(ErrorCode.MAX_MEMBERS_BELOW_CURRENT)
            room.maxMembers = it
        }
        request.isPublic?.let { toPublic ->
            if (room.isPublic != toPublic) {
                room.isPublic = toPublic
                room.inviteCode = if (toPublic) null else inviteCodeGenerator.generate()
            }
        }
        return toDetail(room, RoomRole.OWNER)
    }

    /** 4.6 룸 삭제 (방장). 멤버는 DB FK cascade, 미션·인증·데코는 각 브랜치에서 cascade 추가. */
    @Transactional
    fun delete(userId: Long, roomId: Long) {
        val room = getRoom(roomId)
        requireOwner(room, userId)
        roomMemberRepository.deleteAllByRoomId(roomId)
        roomRepository.delete(room)
    }

    /** 4.11 초대코드 재발급 (방장, 비공개 룸만). 기존 코드는 즉시 무효. */
    @Transactional
    fun regenerateInviteCode(userId: Long, roomId: Long): InviteCodeResponse {
        val room = getRoom(roomId)
        requireOwner(room, userId)
        if (room.isPublic) throw BusinessException(ErrorCode.ROOM_NOT_PRIVATE)
        room.inviteCode = inviteCodeGenerator.generate()
        return InviteCodeResponse(room.inviteCode!!)
    }

    // ---------- 참여 / 탈퇴 / 멤버 관리 ----------

    /** 4.7 공개 룸 참여 */
    @Transactional
    fun joinPublic(userId: Long, roomId: Long): RoomDetailResponse {
        val room = roomRepository.findByIdForUpdate(roomId) ?: throw NotFoundException(ErrorCode.ROOM_NOT_FOUND)
        if (!room.isPublic) throw BusinessException(ErrorCode.ROOM_NOT_PUBLIC)
        addMember(room, userId)
        return toDetail(room, RoomRole.MEMBER)
    }

    /** 4.8 초대코드로 비공개 룸 참여 */
    @Transactional
    fun joinByInviteCode(userId: Long, inviteCode: String): RoomDetailResponse {
        val found = roomRepository.findByInviteCode(inviteCode.trim().uppercase())
            ?: throw NotFoundException(ErrorCode.INVALID_INVITE_CODE)
        val room = roomRepository.findByIdForUpdate(found.id) ?: throw NotFoundException(ErrorCode.INVALID_INVITE_CODE)
        addMember(room, userId)
        return toDetail(room, RoomRole.MEMBER)
    }

    /** 4.9 룸 나가기. 방장은 다른 멤버가 남아 있으면 불가. 마지막 멤버가 나가면 룸 삭제. */
    @Transactional
    fun leave(userId: Long, roomId: Long) {
        val room = roomRepository.findByIdForUpdate(roomId) ?: throw NotFoundException(ErrorCode.ROOM_NOT_FOUND)
        val membership = roomMemberRepository.findByIdRoomIdAndIdUserId(roomId, userId) ?: throw ForbiddenException()
        val count = roomMemberRepository.countByIdRoomId(roomId)
        if (membership.role == RoomRole.OWNER && count > 1) throw BusinessException(ErrorCode.OWNER_CANNOT_LEAVE)
        roomMemberRepository.delete(membership)
        if (count <= 1) roomRepository.delete(room)
    }

    /** 4.12 멤버 내보내기 (방장) */
    @Transactional
    fun kick(ownerId: Long, roomId: Long, targetUserId: Long) {
        val room = getRoom(roomId)
        requireOwner(room, ownerId)
        if (targetUserId == ownerId) throw BusinessException(ErrorCode.CANNOT_KICK_SELF)
        val membership = roomMemberRepository.findByIdRoomIdAndIdUserId(roomId, targetUserId)
            ?: throw NotFoundException(ErrorCode.NOT_A_MEMBER)
        roomMemberRepository.delete(membership)
    }

    /** 4.13 방장 위임 (방장). 기존 방장은 MEMBER로 남는다. */
    @Transactional
    fun transferOwner(ownerId: Long, roomId: Long, targetUserId: Long): RoomDetailResponse {
        val room = getRoom(roomId)
        requireOwner(room, ownerId)
        if (targetUserId == ownerId) throw BusinessException(ErrorCode.ALREADY_OWNER)
        val target = roomMemberRepository.findByIdRoomIdAndIdUserId(roomId, targetUserId)
            ?: throw NotFoundException(ErrorCode.NOT_A_MEMBER)
        val current = roomMemberRepository.findByIdRoomIdAndIdUserId(roomId, ownerId) ?: throw ForbiddenException()
        target.role = RoomRole.OWNER
        current.role = RoomRole.MEMBER
        room.ownerId = targetUserId
        return toDetail(room, RoomRole.MEMBER)
    }

    // ---------- 다른 도메인에서 쓰는 조회 ----------

    fun isMember(roomId: Long, userId: Long) = roomMemberRepository.existsByIdRoomIdAndIdUserId(roomId, userId)
    fun countRoomsOf(userId: Long) = roomMemberRepository.countByIdUserId(userId)
    fun ownsAnyRoom(userId: Long) = roomRepository.existsByOwnerId(userId)

    // ---------- 내부 ----------

    /** 오늘의 APP 미션에 인증(대기/승인)을 올린 멤버 ID */
    private fun todayDoneUserIds(roomId: Long): Set<Long> =
        appMissionService.getOrCreateTodayOrNull(roomId)?.let { proofRepository.findUserIdsWithOpenProof(it.id) } ?: emptySet()

    private fun getRoom(roomId: Long): Room =
        roomRepository.findByIdOrNull(roomId) ?: throw NotFoundException(ErrorCode.ROOM_NOT_FOUND)

    private fun requireOwner(room: Room, userId: Long) {
        if (!room.isOwner(userId)) throw ForbiddenException()
    }

    private fun requireMember(roomId: Long, userId: Long) {
        if (!roomMemberRepository.existsByIdRoomIdAndIdUserId(roomId, userId)) throw ForbiddenException()
    }

    /** 호출 전에 룸 행 잠금(findByIdForUpdate)이 걸려 있어야 정원 검사가 안전하다. */
    private fun addMember(room: Room, userId: Long) {
        if (roomMemberRepository.existsByIdRoomIdAndIdUserId(room.id, userId)) throw BusinessException(ErrorCode.ALREADY_JOINED)
        if (roomMemberRepository.countByIdRoomId(room.id) >= room.maxMembers) throw BusinessException(ErrorCode.ROOM_FULL)
        roomMemberRepository.save(RoomMember(RoomMemberId(room.id, userId), RoomRole.MEMBER))
    }

    private fun toSummary(rc: RoomWithCount, isHot: Boolean, joined: Boolean) = RoomSummaryResponse(
        roomId = rc.room.id,
        name = rc.room.name,
        description = rc.room.description,
        isPublic = rc.room.isPublic,
        memberCount = rc.memberCount,
        maxMembers = rc.room.maxMembers,
        thumbnailUrl = rc.room.thumbnailUrl,
        isHot = isHot,
        joined = joined,
    )

    private fun toDetail(room: Room, myRole: RoomRole?): RoomDetailResponse {
        val owner = userRepository.findByIdOrNull(room.ownerId) ?: throw NotFoundException(ErrorCode.USER_NOT_FOUND)
        return RoomDetailResponse(
            roomId = room.id,
            name = room.name,
            description = room.description,
            isPublic = room.isPublic,
            inviteCode = if (myRole != null) room.inviteCode else null, // 멤버에게만 노출
            owner = UserBrief.from(owner),
            memberCount = roomMemberRepository.countByIdRoomId(room.id),
            maxMembers = room.maxMembers,
            myRole = myRole,
            todayMission = if (myRole != null) appMissionService.getOrCreateTodayOrNull(room.id)?.let { TodayMissionBrief(it.id, it.content) } else null,
            createdAt = room.createdAt,
        )
    }
}
