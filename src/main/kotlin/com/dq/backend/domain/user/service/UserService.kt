package com.dq.backend.domain.user.service

import com.dq.backend.domain.auth.service.RefreshTokenService
import com.dq.backend.domain.proof.entity.ProofStatus
import com.dq.backend.domain.proof.repository.ProofRepository
import com.dq.backend.domain.room.repository.RoomMemberRepository
import com.dq.backend.domain.user.dto.ChangePasswordRequest
import com.dq.backend.domain.user.dto.FcmTokenRequest
import com.dq.backend.domain.user.dto.UpdateMeRequest
import com.dq.backend.domain.user.dto.UserMeResponse
import com.dq.backend.domain.user.dto.UserPublicResponse
import com.dq.backend.domain.user.dto.UserStats
import com.dq.backend.domain.user.entity.User
import com.dq.backend.domain.user.repository.UserRepository
import com.dq.backend.global.error.BusinessException
import com.dq.backend.global.error.ErrorCode
import com.dq.backend.global.error.NotFoundException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class UserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val refreshTokenService: RefreshTokenService,
    private val roomMemberRepository: RoomMemberRepository,
    private val proofRepository: ProofRepository,
) {

    fun getUser(userId: Long): User =
        userRepository.findByIdOrNull(userId) ?: throw NotFoundException(ErrorCode.USER_NOT_FOUND)

    fun getMe(userId: Long): UserMeResponse {
        val user = getUser(userId)
        return UserMeResponse.from(user, statsOf(user))
    }

    fun getPublicProfile(userId: Long): UserPublicResponse {
        val user = getUser(userId)
        return UserPublicResponse.from(user, statsOf(user))
    }

    @Transactional
    fun updateMe(userId: Long, request: UpdateMeRequest): UserMeResponse {
        val user = getUser(userId)
        request.nickname?.let { nickname ->
            if (userRepository.existsByNicknameAndIdNot(nickname, userId)) {
                throw BusinessException(ErrorCode.DUPLICATE_NICKNAME)
            }
            user.nickname = nickname
        }
        request.avatar?.let { a ->
            // TODO(feat/reward): 보유하지 않은 아이템이면 ITEM_NOT_OWNED(403)
            user.avatarOrEmpty.apply {
                hair = a.hair
                top = a.top
                bottom = a.bottom
                accessory = a.accessory
            }
        }
        return UserMeResponse.from(user, statsOf(user))
    }

    /** 비밀번호 변경 (api.md 3.5). 성공 시 모든 기기의 Refresh Token을 폐기한다. */
    @Transactional
    fun changePassword(userId: Long, request: ChangePasswordRequest) {
        val user = getUser(userId)
        if (!passwordEncoder.matches(request.currentPassword, user.passwordHash)) {
            throw BusinessException(ErrorCode.INVALID_CREDENTIALS, "현재 비밀번호가 일치하지 않습니다.")
        }
        if (request.currentPassword == request.newPassword) {
            throw BusinessException(ErrorCode.VALIDATION_ERROR, "새 비밀번호는 현재 비밀번호와 달라야 합니다.")
        }
        user.passwordHash = requireNotNull(passwordEncoder.encode(request.newPassword))
        refreshTokenService.revokeAll(userId)
    }

    @Transactional
    fun updateFcmToken(userId: Long, request: FcmTokenRequest) {
        getUser(userId).fcmToken = request.token
    }

    private fun statsOf(user: User) = UserStats(
        roomCount = roomMemberRepository.countByIdUserId(user.id),
        completedMissionCount = proofRepository.countByUserIdAndStatus(user.id, ProofStatus.APPROVED),
    )
}
