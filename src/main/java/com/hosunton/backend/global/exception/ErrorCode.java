package com.hosunton.backend.global.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    // 400
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "입력값을 다시 확인해 주세요"),
    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "비밀번호가 서로 달라요"),
    TERMS_REQUIRED(HttpStatus.BAD_REQUEST, "필수 약관에 동의해 주세요"),
    AMOUNT_REQUIRED(HttpStatus.BAD_REQUEST, "금액을 입력해 주세요"),
    REASON_REGRET_REQUIRED(HttpStatus.BAD_REQUEST, "이유와 후회 여부를 선택해 주세요"),
    FUTURE_DATE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "미래 날짜는 선택할 수 없어요"),
    INVALID_BUDGET(HttpStatus.BAD_REQUEST, "1원 이상 입력해 주세요"),

    // 401
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호를 다시 확인해 주세요"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "다시 로그인해 주세요"),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "다시 로그인해 주세요"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "다시 로그인해 주세요"),

    // 404
    NOT_FOUND(HttpStatus.NOT_FOUND, "대상을 찾을 수 없어요"),

    // 409
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 가입된 이메일이에요"),
    BUDGET_NOT_SET(HttpStatus.CONFLICT, "주간 예산을 먼저 설정해 주세요"),
    MISSION_ALREADY_IN_PROGRESS(HttpStatus.CONFLICT, "진행 중인 미션을 먼저 마무리해 주세요"),
    RECOMMENDATION_EXPIRED(HttpStatus.CONFLICT, "추천이 새로 바뀌었어요. 다시 골라 주세요"),
    ALREADY_CHECKED_TODAY(HttpStatus.CONFLICT, "오늘은 이미 체크했어요"),
    MISSION_NOT_ACTIVE(HttpStatus.CONFLICT, "진행 중인 미션이 아니에요"),

    // 422
    NOT_ENOUGH_RECORDS(HttpStatus.UNPROCESSABLE_CONTENT, "기록이 5건 이상이면 맞춤 미션을 추천해 드려요"),

    // 500
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "잠시 후 다시 시도해 주세요");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}