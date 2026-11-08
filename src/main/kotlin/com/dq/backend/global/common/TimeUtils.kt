package com.dq.backend.global.common

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** "오늘" 판정은 KST 기준 (api.md 1.1) */
object TimeUtils {
    val KST: ZoneId = ZoneId.of("Asia/Seoul")

    fun todayKst(now: Instant = Instant.now()): LocalDate = now.atZone(KST).toLocalDate()

    /** 해당 KST 날짜의 23:59:59 를 Instant 로 */
    fun endOfDayKst(date: LocalDate): Instant = date.atTime(LocalTime.of(23, 59, 59)).atZone(KST).toInstant()
}
