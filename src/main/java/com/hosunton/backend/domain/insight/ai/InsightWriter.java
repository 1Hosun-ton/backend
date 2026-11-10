package com.hosunton.backend.domain.insight.ai;

/**
 * 집계 통계(JSON)를 받아 인사이트 문장을 만드는 LLM 호출부.
 * 구현체를 바꿔 끼울 수 있도록 인터페이스로 분리한다 (현재 Gemini).
 */
public interface InsightWriter {

    /** API 키 등 호출에 필요한 설정이 있는지. false면 바로 대체 문장을 쓴다. */
    boolean isAvailable();

    /**
     * @param statsJson 코드로 계산한 집계 통계 (메모 원문·개인정보 없음)
     * @throws InsightGenerationException 호출 실패·응답 형식 오류
     */
    InsightText write(String statsJson);
}
