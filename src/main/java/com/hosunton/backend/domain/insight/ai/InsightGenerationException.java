package com.hosunton.backend.domain.insight.ai;

/**
 * LLM 호출 실패, 응답 형식 오류, 수치 불일치. 사용자에게 노출하지 않고 대체 문장으로 처리한다.
 */
public class InsightGenerationException extends RuntimeException {

    public InsightGenerationException(String message) {
        super(message);
    }

    public InsightGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
