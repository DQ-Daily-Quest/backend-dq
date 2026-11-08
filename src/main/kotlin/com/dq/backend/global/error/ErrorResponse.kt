package com.dq.backend.global.error

import java.time.Instant

/** 에러 응답 형식 (api.md 1.3). 모든 에러는 이 구조로 내려간다. */
data class ErrorResponse(
    val code: String,
    val message: String,
    val timestamp: Instant = Instant.now(),
) {
    companion object {
        fun of(errorCode: ErrorCode, message: String = errorCode.message) =
            ErrorResponse(code = errorCode.name, message = message)
    }
}
