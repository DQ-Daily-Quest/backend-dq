package com.dq.backend.domain.room.service

import com.dq.backend.domain.room.repository.RoomRepository
import org.springframework.stereotype.Component
import java.security.SecureRandom

/** 6자리 초대코드. 혼동되는 문자(0/O, 1/I)는 제외한다. */
@Component
class InviteCodeGenerator(private val roomRepository: RoomRepository) {
    private val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private val random = SecureRandom()

    fun generate(): String {
        repeat(10) {
            val code = (1..6).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
            if (!roomRepository.existsByInviteCode(code)) return code
        }
        throw IllegalStateException("초대코드 생성에 실패했습니다.")
    }
}
