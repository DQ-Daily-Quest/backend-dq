package com.dq.backend.domain.proof.entity

import com.dq.backend.global.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

enum class MediaType { IMAGE, VIDEO, TEXT }
enum class ProofStatus { PENDING, APPROVED, REJECTED, EXPIRED }

/** proofs 테이블 (api.md 6장). */
@Entity
@Table(name = "proofs")
class Proof(
    @Column(name = "mission_id", nullable = false)
    val missionId: Long,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 10)
    val mediaType: MediaType,

    @Column(name = "media_url", length = 512)
    val mediaUrl: String? = null,

    @Column(name = "thumbnail_url", length = 512)
    val thumbnailUrl: String? = null,

    @Column(length = 300)
    val text: String? = null,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    var status: ProofStatus = ProofStatus.PENDING
        protected set

    @Column(name = "finalized_at")
    var finalizedAt: Instant? = null
        protected set

    fun isPending() = status == ProofStatus.PENDING

    fun finalize(newStatus: ProofStatus, now: Instant = Instant.now()) {
        check(isPending()) { "이미 확정된 인증" }
        status = newStatus
        finalizedAt = now
    }
}
