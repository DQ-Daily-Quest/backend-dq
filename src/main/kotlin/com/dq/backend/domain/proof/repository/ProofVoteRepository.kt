package com.dq.backend.domain.proof.repository

import com.dq.backend.domain.proof.entity.ProofVote
import com.dq.backend.domain.proof.entity.ProofVoteId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface ProofVoteRepository : JpaRepository<ProofVote, ProofVoteId> {
    fun findAllByIdProofId(proofId: Long): List<ProofVote>
    fun findByIdProofIdAndIdVoterId(proofId: Long, voterId: Long): ProofVote?

    @Modifying
    @Query("delete from ProofVote v where v.id.proofId = :proofId")
    fun deleteAllByProofId(proofId: Long): Int
}
