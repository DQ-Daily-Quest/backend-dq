package com.dq.backend.domain.user.controller

import com.dq.backend.domain.user.dto.ChangePasswordRequest
import com.dq.backend.domain.user.dto.FcmTokenRequest
import com.dq.backend.domain.user.dto.UpdateMeRequest
import com.dq.backend.domain.user.dto.UserMeResponse
import com.dq.backend.domain.user.dto.UserPublicResponse
import com.dq.backend.domain.user.service.UserService
import com.dq.backend.global.security.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@Tag(name = "User", description = "사용자 (api.md 3장)")
@RestController
@RequestMapping("/api/v1/users")
class UserController(private val userService: UserService) {

    @Operation(summary = "내 정보")
    @GetMapping("/me")
    fun me(@CurrentUser userId: Long): UserMeResponse = userService.getMe(userId)

    @Operation(summary = "내 정보 수정 (닉네임, 아바타 구성)")
    @PatchMapping("/me")
    fun updateMe(@CurrentUser userId: Long, @Valid @RequestBody request: UpdateMeRequest): UserMeResponse =
        userService.updateMe(userId, request)

    @Operation(summary = "다른 사용자 공개 프로필")
    @GetMapping("/{userId}")
    fun publicProfile(@PathVariable userId: Long): UserPublicResponse = userService.getPublicProfile(userId)

    @Operation(summary = "비밀번호 변경 (성공 시 모든 Refresh Token 폐기)")
    @PatchMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun changePassword(@CurrentUser userId: Long, @Valid @RequestBody request: ChangePasswordRequest) =
        userService.changePassword(userId, request)

    @Operation(summary = "푸시 알림 토큰 등록")
    @PutMapping("/me/fcm-token")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun updateFcmToken(@CurrentUser userId: Long, @Valid @RequestBody request: FcmTokenRequest) =
        userService.updateFcmToken(userId, request)
}
