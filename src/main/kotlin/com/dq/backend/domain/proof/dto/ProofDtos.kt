package com.dq.backend.domain.proof.dto

import com.dq.backend.domain.mission.dto.RewardResponse
import com.dq.backend.domain.mission.entity.Mission
import com.dq.backend.domain.mission.entity.MissionType
import com.dq.backend.domain.proof.entity.MediaType
import com.dq.backend.domain.proof.entity.Proof
import com.dq.backend.domain.proof.entity.ProofStatus
import com.dq.backend.domain.proof.entity.Vote
import com.dq.backend.domain.room.dto.UserBrief
import com.dq.backend.domain.user.dto.AvatarDto
import com.dq.backend.domain.user.entity.User
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant

/** POST /uploads 응답 (api.md 6.1) */
data class UploadResponse(
    val url: String,
    val thumbnailUrl: String?,
    val mediaType: MediaType,
    val width: Int?,
    val height: Int?,
)

/** POST /missions/{missionId}/proofs (api.md 6.2) */
data class SubmitProofRequest(
    @field:Size(max = 512) val mediaUrl: String? = null,
    @field:Size(max = 512) val thumbnailUrl: String? = null,
    @field:NotNull val mediaType: MediaType,
    @field:Size(max = 300, message = "인증 문구는 최대 300자입니다.") val text: String? = null,
)

data class ReactionRequest(@field:NotBlank val emoji: String)

data class ProofUser(val userId: Long, val nickname: String, val avatar: AvatarDto) {
    companion object {
        fun from(u: User) = ProofUser(u.id, u.nickname, AvatarDto.from(u.avatar))
    }
}

data class VoterResponse(val userId: Long, val nickname: String, val vote: Vote)

data class ApprovalResponse(
    val approved: Int,
    val rejected: Int,
    val required: Int,
    val myVote: Vote?,
    val voters: List<VoterResponse>? = null, // 상세(6.3)에서만
)

data class ReactionSummary(val emoji: String, val count: Int, val reacted: Boolean)

/** 6.2 응답 */
data class ProofResponse(
    val proofId: Long,
    val missionId: Long,
    val user: ProofUser,
    val mediaUrl: String?,
    val thumbnailUrl: String?,
    val mediaType: MediaType,
    val text: String?,
    val status: ProofStatus,
    val approval: ApprovalResponse,
    val reactions: List<ReactionSummary>,
    val createdAt: Instant,
)

data class MissionBrief(val missionId: Long, val type: MissionType, val content: String) {
    companion object {
        fun from(m: Mission) = MissionBrief(m.id, m.type, m.content)
    }
}

data class RoomRef(val roomId: Long, val name: String)

/** 6.3 상세 = 6.2 + mission + room (+ voters) */
data class ProofDetailResponse(
    val proofId: Long,
    val missionId: Long,
    val user: ProofUser,
    val mediaUrl: String?,
    val thumbnailUrl: String?,
    val mediaType: MediaType,
    val text: String?,
    val status: ProofStatus,
    val approval: ApprovalResponse,
    val reactions: List<ReactionSummary>,
    val createdAt: Instant,
    val mission: MissionBrief,
    val room: RoomRef,
) {
    companion object {
        fun from(p: ProofResponse, mission: MissionBrief, room: RoomRef) = ProofDetailResponse(
            p.proofId, p.missionId, p.user, p.mediaUrl, p.thumbnailUrl, p.mediaType, p.text, p.status,
            p.approval, p.reactions, p.createdAt, mission, room,
        )
    }
}

/** 6.4 승인/거절 응답 */
data class VoteResponse(
    val proofId: Long,
    val status: ProofStatus,
    val approval: ApprovalResponse,
    val rewardGranted: RewardResponse?,
)

/** 6.6 피드 / 6.7 승인 대기 항목 */
data class FeedItemResponse(
    val proofId: Long,
    val user: ProofUser,
    val mission: FeedMissionBrief,
    val mediaType: MediaType,
    val thumbnailUrl: String?,
    val status: ProofStatus,
    val viewed: Boolean,
    val createdAt: Instant,
)

data class FeedMissionBrief(val missionId: Long, val content: String)

/** 다른 도메인(미션 상세)의 proofs 항목에 쓰는 변환 */
fun Proof.toUserBrief(user: User) = UserBrief.from(user)
