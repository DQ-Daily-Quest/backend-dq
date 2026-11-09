package com.dq.backend.domain.proof.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant

@Embeddable
data class ProofViewId(
    @Column(name = "proof_id") val proofId: Long = 0,
    @Column(name = "user_id") val userId: Long = 0,
) : Serializable

/** proof_views 테이블. 스토리 링 읽음 표시용. */
@Entity
@Table(name = "proof_views")
class ProofView(@EmbeddedId val id: ProofViewId) {
    @Column(name = "viewed_at", nullable = false)
    var viewedAt: Instant = Instant.now()
}
