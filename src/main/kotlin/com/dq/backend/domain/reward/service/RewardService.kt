package com.dq.backend.domain.reward.service

import com.dq.backend.domain.mission.dto.RewardItemBrief
import com.dq.backend.domain.mission.dto.RewardResponse
import com.dq.backend.domain.mission.entity.Mission
import com.dq.backend.domain.mission.entity.RewardType
import com.dq.backend.domain.reward.entity.CoinReason
import com.dq.backend.domain.reward.entity.CoinTransaction
import com.dq.backend.domain.reward.repository.CoinTransactionRepository
import com.dq.backend.domain.user.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * 보상 지급 (api.md 7장 "보상 지급 규칙"). 인증 확정과 같은 트랜잭션에서 호출된다(MANDATORY).
 * - MEMBER 미션: 코인 지급 + coin_transactions 기록
 * - APP 미션: 아이템 지급. TODO(feat/reward): items/user_items 생성 후 구현 (이미 보유 시 가격 50% 코인 대체)
 */
@Service
class RewardService(
    private val userRepository: UserRepository,
    private val coinTransactionRepository: CoinTransactionRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(propagation = Propagation.MANDATORY)
    fun grantForApprovedProof(userId: Long, mission: Mission, proofId: Long): RewardResponse? = when (mission.rewardType) {
        RewardType.COIN -> {
            val amount = mission.rewardCoins ?: 0
            val user = userRepository.findByIdOrNull(userId) ?: return null
            user.addCoins(amount)
            coinTransactionRepository.save(
                CoinTransaction(userId = userId, amount = amount, balanceAfter = user.coins, reason = CoinReason.MISSION_REWARD, refId = proofId),
            )
            RewardResponse(RewardType.COIN, amount = amount)
        }
        RewardType.ITEM -> {
            // TODO(feat/reward): user_items 에 지급, 중복 보유 시 코인 대체
            log.info("APP 미션 아이템 보상 지급 보류 (feat/reward 전): userId={}, missionId={}, itemId={}", userId, mission.id, mission.rewardItemId)
            mission.rewardItemId?.let { RewardResponse(RewardType.ITEM, item = RewardItemBrief(it, null, null)) }
        }
    }
}
