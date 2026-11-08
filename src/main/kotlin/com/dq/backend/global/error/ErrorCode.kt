package com.dq.backend.global.error

import org.springframework.http.HttpStatus

/**
 * API 공통 에러 코드. docs/api.md 1.5 및 각 섹션의 에러 코드와 1:1로 대응한다.
 * 도메인별 코드는 해당 기능 브랜치에서 이 enum에 추가한다.
 */
enum class ErrorCode(val status: HttpStatus, val message: String) {
    // 공통 (api.md 1.5)
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "토큰이 만료되었습니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "리소스를 찾을 수 없습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),
}
