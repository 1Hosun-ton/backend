package com.hosunton.backend.domain.insight.analysis;

import com.hosunton.backend.domain.expense.ExpenseItem;
import com.hosunton.backend.domain.expense.ExpenseReason;

import java.time.LocalDate;
import java.util.List;

/**
 * 코드로 계산한 기간 통계. LLM은 이 값을 문장으로만 풀어 쓰고 숫자를 새로 만들지 않는다.
 *
 * @param previous 직전 기간에 기록이 없으면 null (비교 문구를 숨긴다)
 * @param pattern  조건을 만족하는 패턴이 없으면 null
 */
public record InsightAnalysis(
        long totalAmount,
        int expenseCount,
        int regretCount,
        List<ReasonShare> reasonBreakdown,
        List<TrendPoint> trend,
        PreviousSummary previous,
        Pattern pattern
) {

    public boolean hasPattern() {
        return pattern != null;
    }

    /**
     * @param ratio                 기간 전체 금액 대비 비중(%), 소수 둘째 자리 반올림
     * @param previousRatio         직전 기간 비중, 직전 기간 기록이 없으면 null
     * @param changePercentagePoint ratio - previousRatio (%p), 직전 기간 기록이 없으면 null
     */
    public record ReasonShare(ExpenseReason reason, long amount, int count, double ratio,
                              Double previousRatio, Double changePercentagePoint) {
    }

    /** 주간은 날짜별, 월간은 주차별(주차의 그 달 안 첫날). */
    public record TrendPoint(LocalDate date, long amount) {
    }

    /**
     * @param totalAmountChangeRate 직전 기간 대비 총액 증감률(%), 소수 첫째 자리 반올림
     */
    public record PreviousSummary(long totalAmount, int regretCount, double totalAmountChangeRate) {
    }

    /**
     * 핵심 패턴: 같은 기간·같은 항목에서 해당 이유의 건당 평균이 다른 이유 평균보다 20% 이상 높은 조합.
     *
     * @param comboAverage  이유×항목 건당 평균 (원 단위 반올림)
     * @param othersAverage 같은 항목의 다른 이유 건당 평균 (원 단위 반올림)
     * @param itemTotal     해당 항목 전체 지출 합계
     */
    public record Pattern(ExpenseReason reason, ExpenseItem item, int comboCount, long comboTotal,
                          long comboAverage, long othersAverage, long itemTotal) {
    }
}
