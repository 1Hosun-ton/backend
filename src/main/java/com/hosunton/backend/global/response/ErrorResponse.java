package com.hosunton.backend.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hosunton.backend.global.exception.ErrorCode;

import java.util.List;

public record ErrorResponse(ErrorBody error) {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ErrorBody(String code, String message, List<FieldError> fieldErrors) {
    }

    public record FieldError(String field, String message) {
    }

    public static ErrorResponse of(ErrorCode errorCode) {
        return of(errorCode, List.of());
    }

    public static ErrorResponse of(ErrorCode errorCode, List<FieldError> fieldErrors) {
        return new ErrorResponse(new ErrorBody(errorCode.name(), errorCode.getMessage(), fieldErrors));
    }
}