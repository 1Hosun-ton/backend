package com.hosunton.backend.domain.insight.ai;

/**
 * LLM(또는 대체 템플릿)이 만든 인사이트 문장.
 *
 * @param insight               핵심 인사이트 한 줄
 * @param comparison            직전 기간 대비 문장 (직전 기간 기록이 없으면 null)
 * @param suggestion            권유형 대안 행동 하나
 * @param questionForReflection 성찰 질문 하나
 */
public record InsightText(String insight, String comparison, String suggestion, String questionForReflection) {
}
