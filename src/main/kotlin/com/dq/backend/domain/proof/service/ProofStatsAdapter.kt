package com.dq.backend.domain.proof.service

import com.dq.backend.domain.mission.dto.MissionProofItem
import com.dq.backend.domain.mission.dto.MyProofSummary
import com.dq.backend.domain.mission.service.MissionProofStatsPort
import com.dq.backend.domain.proof.repository.ProofRepository
import com.dq.backend.domain.room.dto.UserBrief
import com.dq.backend.domain.user.repository.UserRepository
import org.springframework.stereotype.Component

/** 미션 응답에 들어가는 인증 통계의 실제 구현 (api.md 5.1 myProof·proofCount, 5.4 proofs). */
@Component
class ProofStatsAdapter(
    private val proofRepository: ProofRepository,
    private val userRepository: UserRepository,
) : MissionProofStatsPort {

    override fun proofCounts(missionIds: Collection<Long>): Map<Long, Long> =
        if (missionIds.isEmpty()) emptyMap() else proofRepository.countByMissionIds(missionIds).associate { it.missionId to it.cnt }

    override fun myProofs(userId: Long, missionIds: Collection<Long>): Map<Long, MyProofSummary> =
        if (missionIds.isEmpty()) emptyMap()
        else proofRepository.findMineByMissionIds(userId, missionIds).associate { it.missionId to MyProofSummary(it.id, it.status.name) }

    override fun proofsOf(missionId: Long): List<MissionProofItem> {
        val proofs = proofRepository.findAllByMissionIdOrderByCreatedAtDesc(missionId)
        if (proofs.isEmpty()) return emptyList()
        val users = userRepository.findAllById(proofs.map { it.userId }.distinct()).associateBy { it.id }
        return proofs.mapNotNull { p ->
            users[p.userId]?.let { MissionProofItem(p.id, UserBrief.from(it), p.status.name, p.thumbnailUrl, p.createdAt) }
        }
    }

    override fun hasAnyProof(missionId: Long): Boolean = proofRepository.existsByMissionId(missionId)
}
