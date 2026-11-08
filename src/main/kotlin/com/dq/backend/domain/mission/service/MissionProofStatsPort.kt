package com.dq.backend.domain.mission.service

import com.dq.backend.domain.mission.dto.MissionProofItem
import com.dq.backend.domain.mission.dto.MyProofSummary
import org.springframework.stereotype.Component

/**
 * 미션 응답에 들어가는 인증(proof) 통계. 인증 도메인이 구현되기 전까지는 빈 값을 돌려준다.
 * TODO(feat/proof): ProofService 가 이 포트를 구현하고 아래 NoProofStats 를 제거한다.
 */
interface MissionProofStatsPort {
    fun proofCounts(missionIds: Collection<Long>): Map<Long, Long>
    fun myProofs(userId: Long, missionIds: Collection<Long>): Map<Long, MyProofSummary>
    fun proofsOf(missionId: Long): List<MissionProofItem>
    fun hasAnyProof(missionId: Long): Boolean
}

@Component
class NoProofStats : MissionProofStatsPort {
    override fun proofCounts(missionIds: Collection<Long>): Map<Long, Long> = emptyMap()
    override fun myProofs(userId: Long, missionIds: Collection<Long>): Map<Long, MyProofSummary> = emptyMap()
    override fun proofsOf(missionId: Long): List<MissionProofItem> = emptyList()
    override fun hasAnyProof(missionId: Long): Boolean = false
}
