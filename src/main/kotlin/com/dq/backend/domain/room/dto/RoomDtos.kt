package com.dq.backend.domain.room.dto

import com.dq.backend.domain.room.entity.Room
import com.dq.backend.domain.room.entity.RoomMember
import com.dq.backend.domain.room.entity.RoomRole
import com.dq.backend.domain.user.dto.AvatarDto
import com.dq.backend.domain.user.entity.User
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant

/** POST /rooms (api.md 4.3) */
data class CreateRoomRequest(
    @field:NotBlank @field:Size(min = 2, max = 20, message = "룸 이름은 2~20자여야 합니다.")
    val name: String,
    @field:Size(max = 100, message = "설명은 최대 100자입니다.")
    val description: String? = null,
    @field:NotNull
    val isPublic: Boolean,
    @field:Min(2) @field:Max(30)
    val maxMembers: Int = 10,
)

data class CreateRoomResponse(
    val roomId: Long,
    val name: String,
    val isPublic: Boolean,
    val inviteCode: String?,
    val ownerId: Long,
) {
    companion object {
        fun from(room: Room) = CreateRoomResponse(room.id, room.name, room.isPublic, room.inviteCode, room.ownerId)
    }
}

/** PATCH /rooms/{roomId} (api.md 4.5) — 보내는 필드만 수정 */
data class UpdateRoomRequest(
    @field:Size(min = 2, max = 20, message = "룸 이름은 2~20자여야 합니다.")
    val name: String? = null,
    @field:Size(max = 100, message = "설명은 최대 100자입니다.")
    val description: String? = null,
    val isPublic: Boolean? = null,
    @field:Min(2) @field:Max(30)
    val maxMembers: Int? = null,
)

/** POST /rooms/join (api.md 4.8) */
data class JoinByInviteCodeRequest(@field:NotBlank val inviteCode: String)

/** POST /rooms/{roomId}/transfer-owner (api.md 4.13) */
data class TransferOwnerRequest(@field:NotNull val userId: Long)

data class InviteCodeResponse(val inviteCode: String)

/** 목록 항목 (api.md 4.1, 4.2) */
data class RoomSummaryResponse(
    val roomId: Long,
    val name: String,
    val description: String?,
    val isPublic: Boolean,
    val memberCount: Long,
    val maxMembers: Int,
    val thumbnailUrl: String?,
    val isHot: Boolean,
    val joined: Boolean,
    // 4.2 내 룸 목록에서만 채워짐. TODO(feat/mission, feat/proof): 실제 집계로 교체
    val todayMissionDone: Boolean? = null,
    val pendingProofCount: Long? = null,
)

data class UserBrief(val userId: Long, val nickname: String) {
    companion object {
        fun from(user: User) = UserBrief(user.id, user.nickname)
    }
}

/** GET /rooms/{roomId} (api.md 4.4) */
data class RoomDetailResponse(
    val roomId: Long,
    val name: String,
    val description: String?,
    val isPublic: Boolean,
    val inviteCode: String?,
    val owner: UserBrief,
    val memberCount: Long,
    val maxMembers: Int,
    val myRole: RoomRole?,
    // TODO(feat/mission): 오늘의 APP 미션 요약 { missionId, content }
    val todayMission: Any? = null,
    val createdAt: Instant,
)

/** GET /rooms/{roomId}/members (api.md 4.10) */
data class RoomMemberResponse(
    val userId: Long,
    val nickname: String,
    val role: RoomRole,
    val avatar: AvatarDto,
    val todayMissionDone: Boolean, // TODO(feat/proof): 실제 집계
    val joinedAt: Instant,
) {
    companion object {
        fun from(member: RoomMember, user: User) = RoomMemberResponse(
            userId = user.id,
            nickname = user.nickname,
            role = member.role,
            avatar = AvatarDto.from(user.avatar),
            todayMissionDone = false,
            joinedAt = member.joinedAt,
        )
    }
}
