package com.dq.backend.domain.mission.dto

import com.dq.backend.domain.mission.entity.Mission
import com.dq.backend.domain.mission.entity.MissionType
import com.dq.backend.domain.mission.entity.RewardType
import com.dq.backend.domain.room.dto.UserBrief
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate

/** POST /rooms/{roomId}/missions (api.md 5.3) */
data class CreateMemberMissionRequest(
    @field:NotBlank @field:Size(min = 2, max = 100, message = "미션 내용은 2~100자여야 합니다.")
    val content: String,
    @field:NotNull @field:Min(10) @field:Max(500)
    val rewardCoins: Int,
    val dueAt: Instant? = null,
)

/** PATCH /missions/{missionId} (api.md 5.5) */
data class UpdateMemberMissionRequest(
    @field:Size(min = 2, max = 100, message = "미션 내용은 2~100자여야 합니다.")
    val content: String? = null,
    val dueAt: Instant? = null,
)

/** 보상 아이템 요약. TODO(feat/reward): items 테이블 연결 후 name/imageUrl 채움 */
data class RewardItemBrief(val itemId: Long, val name: String?, val imageUrl: String?)

data class RewardResponse(
    val type: RewardType,
    val item: RewardItemBrief? = null,
    val amount: Int? = null,
)

/** 요청자가 제출한 인증 요약. TODO(feat/proof): 실제 값 */
data class MyProofSummary(val proofId: Long, val status: String)

/** 5.1 미션 객체 */
data class MissionResponse(
    val missionId: Long,
    val type: MissionType,
    val content: String,
    val reward: RewardResponse,
    val createdBy: UserBrief?,
    val date: LocalDate?,
    val dueAt: Instant,
    val myProof: MyProofSummary?,
    val proofCount: Long,
    val createdAt: Instant,
) {
    companion object {
        fun from(m: Mission, createdBy: UserBrief?, myProof: MyProofSummary?, proofCount: Long) = MissionResponse(
            missionId = m.id,
            type = m.type,
            content = m.content,
            reward = when (m.rewardType) {
                RewardType.ITEM -> RewardResponse(RewardType.ITEM, item = m.rewardItemId?.let { RewardItemBrief(it, null, null) })
                RewardType.COIN -> RewardResponse(RewardType.COIN, amount = m.rewardCoins)
            },
            createdBy = createdBy,
            date = m.date,
            dueAt = m.dueAt,
            myProof = myProof,
            proofCount = proofCount,
            createdAt = m.createdAt,
        )
    }
}

/** 5.4 미션 상세의 인증 항목. TODO(feat/proof): 실제 값 */
data class MissionProofItem(
    val proofId: Long,
    val user: UserBrief,
    val status: String,
    val thumbnailUrl: String?,
    val createdAt: Instant,
)

/** 5.4 미션 상세 = 5.1 미션 객체 + proofs */
data class MissionDetailResponse(
    val missionId: Long,
    val type: MissionType,
    val content: String,
    val reward: RewardResponse,
    val createdBy: UserBrief?,
    val date: LocalDate?,
    val dueAt: Instant,
    val myProof: MyProofSummary?,
    val proofCount: Long,
    val createdAt: Instant,
    val proofs: List<MissionProofItem>,
) {
    companion object {
        fun from(r: MissionResponse, proofs: List<MissionProofItem>) = MissionDetailResponse(
            r.missionId, r.type, r.content, r.reward, r.createdBy, r.date, r.dueAt, r.myProof, r.proofCount, r.createdAt, proofs,
        )
    }
}

data class RoomBrief(val roomId: Long, val name: String)

/** 5.7 내 미션 모아보기 */
data class MyMissionsByRoomResponse(val room: RoomBrief, val missions: List<MissionResponse>)
