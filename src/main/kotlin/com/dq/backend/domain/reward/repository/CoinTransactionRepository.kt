package com.dq.backend.domain.reward.repository

import com.dq.backend.domain.reward.entity.CoinTransaction
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface CoinTransactionRepository : JpaRepository<CoinTransaction, Long> {
    fun findAllByUserIdOrderByCreatedAtDesc(userId: Long, pageable: Pageable): Page<CoinTransaction>
}
