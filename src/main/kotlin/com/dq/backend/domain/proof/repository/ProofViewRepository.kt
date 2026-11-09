package com.dq.backend.domain.proof.repository

import com.dq.backend.domain.proof.entity.ProofView
import com.dq.backend.domain.proof.entity.ProofViewId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface ProofViewRepository : JpaRepository<ProofView, ProofViewId> {
    @Query("select v.id.proofId from ProofView v where v.id.userId = :userId and v.id.proofId in :proofIds")
    fun findViewedProofIds(userId: Long, proofIds: Collection<Long>): Set<Long>

    @Modifying
    @Query("delete from ProofView v where v.id.proofId = :proofId")
    fun deleteAllByProofId(proofId: Long): Int
}
