package com.dq.backend.domain.auth.controller

import com.dq.backend.domain.auth.dto.LoginRequest
import com.dq.backend.domain.auth.dto.RefreshRequest
import com.dq.backend.domain.auth.dto.SignupRequest
import com.dq.backend.domain.auth.dto.SignupResponse
import com.dq.backend.domain.auth.dto.TokenResponse
import com.dq.backend.domain.auth.service.AuthService
import com.dq.backend.global.security.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Auth", description = "인증 (api.md 2장)")
@RestController
@RequestMapping("/api/v1/auth")
class AuthController(private val authService: AuthService) {

    @Operation(summary = "회원가입")
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    fun signup(@Valid @RequestBody request: SignupRequest): SignupResponse = authService.signup(request)

    @Operation(summary = "로그인 (Access/Refresh Token 발급)")
    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): TokenResponse = authService.login(request)

    @Operation(summary = "토큰 재발급 (Refresh Token 회전)")
    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshRequest): TokenResponse = authService.refresh(request)

    @Operation(summary = "로그아웃 (Refresh Token 폐기)")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(@CurrentUser userId: Long) = authService.logout(userId)
}
