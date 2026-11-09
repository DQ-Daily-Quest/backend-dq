package com.dq.backend.domain.proof.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant

enum class Vote { APPROVE, REJECT }

@Embeddable
data class ProofVoteId(
    @Column(name = "proof_id") val proofId: Long = 0,
    @Column(name = "voter_id") val voterId: Long = 0,
) : Serializable

/** proof_votes 테이블. PK (proof_id, voter_id) — 한 사람이 한 인증에 한 표, 변경 가능. */
@Entity
@Table(name = "proof_votes")
class ProofVote(
    @EmbeddedId val id: ProofVoteId,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    var vote: Vote,
) {
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
}
