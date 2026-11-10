package com.hosunton.backend.domain.insight.dto;

import com.hosunton.backend.domain.expense.ExpenseItem;
import com.hosunton.backend.domain.expense.ExpenseReason;
import com.hosunton.backend.domain.insight.PeriodType;

import java.time.LocalDate;
import java.util.List;

/**
 * GET /api/v1/insights 응답. AI로 정상 생성된 READY 결과는 이 JSON 그대로 insights.result에 저장한다.
 */
public record InsightResponse(
        InsightStatus status,
        long cumulativeExpenseCount,
        int requiredExpenseCount,
        Period period,
        long totalAmount,
        int regretCount,
        KeyInsight keyInsight,
        List<ReasonShare> reasonBreakdown,
        List<TrendPoint> trend,
        String comparisonMessage,
        String suggestion,
        String reflectionQuestion,
        AnalysisSource analysisSource
) {

    public enum InsightStatus {
        /** 누적 기록 5건 미만 */
        INSUFFICIENT_DATA,
        /** 조회 기간에 기록 없음 */
        NO_EXPENSES,
        /** 조건을 만족하는 패턴 없음 */
        NO_PATTERN,
        READY
    }

    public enum AnalysisSource {
        AI,
        /** AI 실패로 규칙 기반 문장을 씀 (저장하지 않음) */
        FALLBACK,
        NONE
    }

    public record Period(PeriodType type, LocalDate startDate, LocalDate endDate) {
    }

    public record KeyInsight(String message, Evidence evidence) {
    }

    /**
     * @param reasonCount 이유×항목 조합 건수
     * @param itemAmount  해당 항목 전체 지출 합계 (예: "근거: 스트레스 해소 2건 · 배달 음식 39,000원")
     */
    public record Evidence(ExpenseReason reason, int reasonCount, ExpenseItem item, long itemAmount) {
    }

    public record ReasonShare(ExpenseReason reason, long amount, double ratio,
                              Double previousRatio, Double changePercentagePoint) {
    }

    public record TrendPoint(LocalDate date, long amount) {
    }

    /** 캐시에서 꺼낸 결과에 현재 누적 건수를 반영한다. */
    public InsightResponse withCumulativeExpenseCount(long count) {
        return new InsightResponse(status, count, requiredExpenseCount, period, totalAmount, regretCount,
                keyInsight, reasonBreakdown, trend, comparisonMessage, suggestion, reflectionQuestion, analysisSource);
    }
}
