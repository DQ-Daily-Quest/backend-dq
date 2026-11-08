package com.dq.backend.domain.auth.repository

import com.dq.backend.domain.auth.entity.RefreshToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface RefreshTokenRepository : JpaRepository<RefreshToken, Long> {
    fun findByTokenHash(tokenHash: String): RefreshToken?

    @Modifying
    @Query("delete from RefreshToken t where t.userId = :userId")
    fun deleteAllByUserId(userId: Long): Int

    @Modifying
    @Query("delete from RefreshToken t where t.expiresAt < :now")
    fun deleteAllExpired(now: Instant): Int
}
