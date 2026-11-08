package com.dq.backend.global.error

/**
 * 서비스 계층에서 던지는 예외. GlobalExceptionHandler가 ErrorResponse로 변환한다.
 * 메시지를 생략하면 ErrorCode의 기본 메시지를 사용한다.
 */
open class BusinessException(
    val errorCode: ErrorCode,
    override val message: String = errorCode.message,
) : RuntimeException(message)

class NotFoundException(errorCode: ErrorCode = ErrorCode.NOT_FOUND, message: String = errorCode.message) :
    BusinessException(errorCode, message)

class ForbiddenException(errorCode: ErrorCode = ErrorCode.FORBIDDEN, message: String = errorCode.message) :
    BusinessException(errorCode, message)
