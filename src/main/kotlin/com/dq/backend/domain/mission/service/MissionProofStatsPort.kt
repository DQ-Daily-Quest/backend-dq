package com.dq.backend.domain.mission.service

import com.dq.backend.domain.mission.dto.MissionProofItem
import com.dq.backend.domain.mission.dto.MyProofSummary

/** 미션 응답에 들어가는 인증(proof) 통계. 구현: domain.proof.service.ProofStatsAdapter */
interface MissionProofStatsPort {
    fun proofCounts(missionIds: Collection<Long>): Map<Long, Long>
    fun myProofs(userId: Long, missionIds: Collection<Long>): Map<Long, MyProofSummary>
    fun proofsOf(missionId: Long): List<MissionProofItem>
    fun hasAnyProof(missionId: Long): Boolean
}
