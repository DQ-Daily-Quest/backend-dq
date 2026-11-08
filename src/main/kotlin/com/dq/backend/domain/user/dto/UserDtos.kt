package com.dq.backend.domain.user.dto

import com.dq.backend.domain.user.entity.Avatar
import com.dq.backend.domain.user.entity.User
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class AvatarDto(
    val hair: Long? = null,
    val top: Long? = null,
    val bottom: Long? = null,
    val accessory: Long? = null,
) {
    companion object {
        fun from(a: Avatar?) = if (a == null) AvatarDto() else AvatarDto(a.hair, a.top, a.bottom, a.accessory)
    }
}

/** 활동 통계. roomCount / completedMissionCount 는 feat/room, feat/proof 에서 실제 집계로 교체한다. */
data class UserStats(
    val roomCount: Long = 0,
    val completedMissionCount: Long = 0,
)

/** GET /users/me (api.md 3.1) */
data class UserMeResponse(
    val userId: Long,
    val email: String,
    val nickname: String,
    val coins: Int,
    val avatar: AvatarDto,
    val stats: UserStats,
    val createdAt: Instant,
) {
    companion object {
        fun from(user: User, stats: UserStats) = UserMeResponse(
            userId = user.id,
            email = user.email,
            nickname = user.nickname,
            coins = user.coins,
            avatar = AvatarDto.from(user.avatar),
            stats = stats,
            createdAt = user.createdAt,
        )
    }
}

/** GET /users/{userId} (api.md 3.3) — 이메일·코인 제외 */
data class UserPublicResponse(
    val userId: Long,
    val nickname: String,
    val avatar: AvatarDto,
    val stats: UserStats,
) {
    companion object {
        fun from(user: User, stats: UserStats) = UserPublicResponse(
            userId = user.id,
            nickname = user.nickname,
            avatar = AvatarDto.from(user.avatar),
            stats = stats,
        )
    }
}

/** PATCH /users/me (api.md 3.2) — 보내는 필드만 수정 */
data class UpdateMeRequest(
    @field:Size(min = 2, max = 12, message = "닉네임은 2~12자여야 합니다.")
    val nickname: String? = null,
    val avatar: AvatarDto? = null,
)

/** PUT /users/me/fcm-token (api.md 3.4) */
data class FcmTokenRequest(
    @field:NotBlank(message = "token은 필수입니다.")
    @field:Size(max = 512)
    val token: String,
)
