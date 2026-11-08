package com.dq.backend.domain.user.entity

import com.dq.backend.global.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/** users 테이블 (api.md 10장). 아바타 착용 정보는 Avatar 임베디드로 컬럼 분리 저장. */
@Entity
@Table(name = "users")
class User(
    @Column(nullable = false, unique = true, length = 255)
    val email: String,

    @Column(name = "password_hash", nullable = false)
    var passwordHash: String,

    @Column(nullable = false, unique = true, length = 12)
    var nickname: String,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(nullable = false)
    var coins: Int = 0

    /** Hibernate는 모든 컬럼이 null인 임베디드를 null로 로드하므로 nullable로 두고 [avatarOrEmpty]로 읽는다. */
    @Embedded
    var avatar: Avatar? = Avatar()

    val avatarOrEmpty: Avatar
        get() = avatar ?: Avatar().also { avatar = it }

    @Column(name = "fcm_token", length = 512)
    var fcmToken: String? = null

    fun addCoins(amount: Int) {
        require(coins + amount >= 0) { "코인이 부족합니다." }
        coins += amount
    }
}
