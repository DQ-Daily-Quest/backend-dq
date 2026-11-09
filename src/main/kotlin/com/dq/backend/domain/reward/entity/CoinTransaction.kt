package com.dq.backend.domain.reward.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

enum class CoinReason { MISSION_REWARD, PURCHASE, ITEM_DUPLICATE_COMPENSATION }

/** coin_transactions 테이블 (api.md 7장). 모든 코인 변동을 기록한다. */
@Entity
@Table(name = "coin_transactions")
class CoinTransaction(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(nullable = false)
    val amount: Int,

    @Column(name = "balance_after", nullable = false)
    val balanceAfter: Int,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val reason: CoinReason,

    @Column(name = "ref_id")
    val refId: Long? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
}
