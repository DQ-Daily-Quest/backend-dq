package com.dq.backend.domain.proof.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.io.Serializable

@Embeddable
data class ProofReactionId(
    @Column(name = "proof_id") val proofId: Long = 0,
    @Column(name = "user_id") val userId: Long = 0,
    @Column(name = "emoji", length = 16) val emoji: String = "",
) : Serializable

/** proof_reactions 테이블. 한 사용자가 한 인증에 같은 이모지는 1번만. */
@Entity
@Table(name = "proof_reactions")
class ProofReaction(@EmbeddedId val id: ProofReactionId)
