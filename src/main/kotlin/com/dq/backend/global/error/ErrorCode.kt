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

    // 인증 (api.md 2장)
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 Refresh Token입니다."),

    // 사용자 (api.md 3장)
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 사용자입니다."),
    ITEM_NOT_OWNED(HttpStatus.FORBIDDEN, "보유하지 않은 아이템입니다."),
    OWNER_MUST_TRANSFER(HttpStatus.CONFLICT, "방장인 룸이 있어 탈퇴할 수 없습니다. 먼저 방장을 위임하거나 룸을 삭제하세요."),

    // 룸 (api.md 4장)
    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 룸입니다."),
    ROOM_NOT_PUBLIC(HttpStatus.FORBIDDEN, "비공개 룸은 초대코드로만 참여할 수 있습니다."),
    ROOM_NOT_PRIVATE(HttpStatus.CONFLICT, "공개 룸에는 초대코드가 없습니다."),
    ALREADY_JOINED(HttpStatus.CONFLICT, "이미 참여한 룸입니다."),
    ROOM_FULL(HttpStatus.CONFLICT, "룸 정원이 가득 찼습니다."),
    INVALID_INVITE_CODE(HttpStatus.NOT_FOUND, "유효하지 않은 초대코드입니다."),
    OWNER_CANNOT_LEAVE(HttpStatus.CONFLICT, "방장은 다른 멤버가 남아 있으면 나갈 수 없습니다. 방장을 위임하거나 룸을 삭제하세요."),
    MAX_MEMBERS_BELOW_CURRENT(HttpStatus.CONFLICT, "최대 인원을 현재 멤버 수보다 작게 설정할 수 없습니다."),
    NOT_A_MEMBER(HttpStatus.NOT_FOUND, "룸 멤버가 아닙니다."),
    CANNOT_KICK_SELF(HttpStatus.CONFLICT, "자기 자신은 내보낼 수 없습니다."),
    ALREADY_OWNER(HttpStatus.CONFLICT, "이미 방장입니다."),

    // 미션 (api.md 5장)
    MISSION_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 미션입니다."),
    MISSION_HAS_PROOFS(HttpStatus.CONFLICT, "이미 인증이 제출된 미션은 수정·삭제할 수 없습니다."),
    APP_MISSION_IMMUTABLE(HttpStatus.CONFLICT, "APP 미션은 수정·삭제할 수 없습니다."),
    NO_ACTIVE_TEMPLATE(HttpStatus.INTERNAL_SERVER_ERROR, "활성 미션 템플릿이 없습니다."),
}
