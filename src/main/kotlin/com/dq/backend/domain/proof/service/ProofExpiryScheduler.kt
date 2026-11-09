package com.dq.backend.domain.proof.service

import com.dq.backend.domain.proof.repository.ProofRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

/** 미션 마감 후 24시간이 지나도 미확정인 인증을 EXPIRED 로 바꾼다 (api.md 6장, 12장 #17 기본안). 매시 10분 실행. */
@Component
class ProofExpiryScheduler(private val proofRepository: ProofRepository) {
    companion object { val GRACE: Duration = Duration.ofHours(24) }

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    @Scheduled(cron = "0 10 * * * *", zone = "Asia/Seoul")
    fun expire() {
        val now = Instant.now()
        val n = proofRepository.expirePending(dueBefore = now.minus(GRACE), now = now)
        if (n > 0) log.info("미확정 인증 {}건 만료 처리", n)
    }
}
