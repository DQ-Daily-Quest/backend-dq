package com.dq.backend.domain.auth.service

import com.dq.backend.domain.auth.dto.LoginRequest
import com.dq.backend.domain.auth.dto.RefreshRequest
import com.dq.backend.domain.auth.dto.SignupRequest
import com.dq.backend.domain.auth.dto.SignupResponse
import com.dq.backend.domain.auth.dto.TokenResponse
import com.dq.backend.domain.auth.dto.UserSummary
import com.dq.backend.domain.user.entity.User
import com.dq.backend.domain.user.repository.UserRepository
import com.dq.backend.global.error.BusinessException
import com.dq.backend.global.error.ErrorCode
import com.dq.backend.global.security.JwtProvider
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtProvider: JwtProvider,
    private val refreshTokenService: RefreshTokenService,
) {

    @Transactional
    fun signup(request: SignupRequest): SignupResponse {
        if (userRepository.existsByEmail(request.email)) throw BusinessException(ErrorCode.DUPLICATE_EMAIL)
        if (userRepository.existsByNickname(request.nickname)) throw BusinessException(ErrorCode.DUPLICATE_NICKNAME)
        val user = userRepository.save(
            User(
                email = request.email,
                passwordHash = requireNotNull(passwordEncoder.encode(request.password)),
                nickname = request.nickname,
            ),
        )
        return SignupResponse.from(user)
    }

    @Transactional
    fun login(request: LoginRequest): TokenResponse {
        val user = userRepository.findByEmail(request.email)
            ?: throw BusinessException(ErrorCode.INVALID_CREDENTIALS)
        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            throw BusinessException(ErrorCode.INVALID_CREDENTIALS)
        }
        return issueTokens(user)
    }

    /** Refresh Token 회전: 기존 토큰 폐기 후 Access/Refresh 모두 새로 발급 (api.md 2.3) */
    @Transactional
    fun refresh(request: RefreshRequest): TokenResponse {
        val userId = refreshTokenService.consume(request.refreshToken)
        val user = userRepository.findByIdOrNull(userId)
            ?: throw BusinessException(ErrorCode.INVALID_REFRESH_TOKEN)
        return issueTokens(user)
    }

    /** 로그아웃: 이 사용자의 Refresh Token을 모두 폐기한다 (api.md 2.4). Access Token은 만료까지 유효. */
    @Transactional
    fun logout(userId: Long) {
        refreshTokenService.revokeAll(userId)
    }

    private fun issueTokens(user: User) = TokenResponse(
        accessToken = jwtProvider.createAccessToken(user.id),
        refreshToken = refreshTokenService.issue(user.id),
        expiresIn = jwtProvider.accessTokenExpirySeconds,
        user = UserSummary.from(user),
    )
}
