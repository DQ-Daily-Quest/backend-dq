package com.dq.backend.domain.user.repository

import com.dq.backend.domain.user.entity.User
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Long> {
    fun findByEmail(email: String): User?
    fun existsByEmail(email: String): Boolean
    fun existsByNickname(nickname: String): Boolean
    fun existsByNicknameAndIdNot(nickname: String, id: Long): Boolean
}
