package com.dq.backend.global.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Component
import java.util.Date
import javax.crypto.SecretKey

/** Access Token(JWT) 발급·검증. Refresh Token은 불투명 랜덤 문자열이라 RefreshTokenService가 담당한다. */
@Component
class JwtProvider(private val props: JwtProperties) {

    private val key: SecretKey = Keys.hmacShaKeyFor(props.secret.toByteArray(Charsets.UTF_8))

    val accessTokenExpirySeconds: Long get() = props.accessTokenExpirySeconds

    fun createAccessToken(userId: Long): String {
        val now = Date()
        val expiry = Date(now.time + props.accessTokenExpirySeconds * 1000)
        return Jwts.builder()
            .subject(userId.toString())
            .issuedAt(now)
            .expiration(expiry)
            .signWith(key)
            .compact()
    }

    /** 유효하면 userId, 만료면 [TokenExpired], 그 외 위조·형식 오류면 [Invalid]. */
    fun parse(token: String): ParseResult = try {
        val claims: Claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload
        ParseResult.Valid(claims.subject.toLong())
    } catch (e: ExpiredJwtException) {
        ParseResult.Expired
    } catch (e: JwtException) {
        ParseResult.Invalid
    } catch (e: IllegalArgumentException) {
        ParseResult.Invalid
    }

    sealed interface ParseResult {
        data class Valid(val userId: Long) : ParseResult
        data object Expired : ParseResult
        data object Invalid : ParseResult
    }
}
