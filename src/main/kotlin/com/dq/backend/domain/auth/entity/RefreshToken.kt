package com.dq.backend.domain.auth.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.Instant

/** refresh_tokens 테이블. 원문은 저장하지 않고 SHA-256 해시만 보관한다. */
@Entity
@Table(name = "refresh_tokens", indexes = [Index(name = "idx_refresh_tokens_user", columnList = "user_id")])
class RefreshToken(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    val tokenHash: String,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    fun isExpired(now: Instant = Instant.now()) = expiresAt.isBefore(now)
}
