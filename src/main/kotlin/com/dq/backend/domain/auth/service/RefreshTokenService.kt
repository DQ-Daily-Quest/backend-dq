package com.dq.backend.domain.auth.service

import com.dq.backend.domain.auth.entity.RefreshToken
import com.dq.backend.domain.auth.repository.RefreshTokenRepository
import com.dq.backend.global.error.BusinessException
import com.dq.backend.global.error.ErrorCode
import com.dq.backend.global.security.JwtProperties
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64

/** Refresh Token 발급·검증·폐기. 원문은 클라이언트만 보관하고 DB에는 해시만 저장한다. */
@Service
class RefreshTokenService(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val props: JwtProperties,
) {
    private val random = SecureRandom()

    @Transactional
    fun issue(userId: Long): String {
        val raw = ByteArray(32).also { random.nextBytes(it) }
            .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
        refreshTokenRepository.save(
            RefreshToken(
                userId = userId,
                tokenHash = hash(raw),
                expiresAt = Instant.now().plusSeconds(props.refreshTokenExpirySeconds),
            ),
        )
        return raw
    }

    /** 검증 후 기존 토큰은 폐기(회전)하고 userId를 돌려준다. */
    @Transactional
    fun consume(raw: String): Long {
        val token = refreshTokenRepository.findByTokenHash(hash(raw))
            ?: throw BusinessException(ErrorCode.INVALID_REFRESH_TOKEN)
        refreshTokenRepository.delete(token)
        if (token.isExpired()) throw BusinessException(ErrorCode.INVALID_REFRESH_TOKEN)
        return token.userId
    }

    @Transactional
    fun revokeAll(userId: Long) {
        refreshTokenRepository.deleteAllByUserId(userId)
    }

    private fun hash(raw: String): String =
        MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
