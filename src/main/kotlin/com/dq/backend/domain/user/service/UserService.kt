package com.dq.backend.domain.user.service

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
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class UserService(private val userRepository: UserRepository) {

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

    @Transactional
    fun updateFcmToken(userId: Long, request: FcmTokenRequest) {
        getUser(userId).fcmToken = request.token
    }

    // TODO(feat/room, feat/proof): room_members / proofs(APPROVED) 집계로 교체
    private fun statsOf(user: User) = UserStats()
}
