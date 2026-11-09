package com.dq.backend.domain.proof.repository

import com.dq.backend.domain.proof.entity.ProofReaction
import com.dq.backend.domain.proof.entity.ProofReactionId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface ProofReactionRepository : JpaRepository<ProofReaction, ProofReactionId> {
    fun findAllByIdProofId(proofId: Long): List<ProofReaction>

    @Modifying
    @Query("delete from ProofReaction r where r.id.proofId = :proofId")
    fun deleteAllByProofId(proofId: Long): Int
}
