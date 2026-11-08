package com.dq.backend.domain.auth.dto

import com.dq.backend.domain.user.entity.User
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/** POST /auth/signup (api.md 2.1) */
data class SignupRequest(
    @field:NotBlank @field:Email(message = "이메일 형식이 아닙니다.")
    val email: String,
    @field:NotBlank @field:Size(min = 8, max = 64, message = "비밀번호는 8~64자여야 합니다.")
    val password: String,
    @field:NotBlank @field:Size(min = 2, max = 12, message = "닉네임은 2~12자여야 합니다.")
    val nickname: String,
)

data class SignupResponse(val userId: Long, val email: String, val nickname: String) {
    companion object {
        fun from(user: User) = SignupResponse(user.id, user.email, user.nickname)
    }
}

/** POST /auth/login (api.md 2.2) */
data class LoginRequest(
    @field:NotBlank val email: String,
    @field:NotBlank val password: String,
)

/** 로그인·재발급 공통 응답 (api.md 2.2) */
data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val user: UserSummary,
)

data class UserSummary(val userId: Long, val nickname: String, val avatarUrl: String?, val coins: Int) {
    companion object {
        // avatarUrl: 아바타 이미지 합성은 클라이언트 담당이라 서버는 null 고정 (api.md 2.2 예시 유지)
        fun from(user: User) = UserSummary(user.id, user.nickname, null, user.coins)
    }
}

/** POST /auth/refresh (api.md 2.3) */
data class RefreshRequest(@field:NotBlank val refreshToken: String)
