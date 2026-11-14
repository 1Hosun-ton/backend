package com.hosunton.backend.domain.insight.analysis;

import com.hosunton.backend.domain.expense.ExpenseItem;
import com.hosunton.backend.domain.expense.ExpenseReason;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 코드로 계산한 기간 통계. LLM은 이 값을 문장으로만 풀어 쓰고 숫자를 새로 만들지 않는다.
 *
 * @param previous 직전 기간에 기록이 없으면 null (비교 문구를 숨긴다)
 * @param pattern  조건을 만족하는 패턴이 없으면 null
 * @param findings 핵심 패턴에서 찾은 발견(계기→결과), 강한 순. pattern 이 null 이면 빈 목록
 */
public record InsightAnalysis(
        long totalAmount,
        int expenseCount,
        int regretCount,
        List<ReasonShare> reasonBreakdown,
        List<TrendPoint> trend,
        PreviousSummary previous,
        Pattern pattern,
        List<Finding> findings
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

    /**
     * 핵심 패턴 기록에서 찾은 발견 하나. 사용자가 "언제·어떤 마음일 때 → 어떻게 됐는지"를 알아차리고
     * 다음에 바꿔 볼 지점을 찾게 하는 재료다. 숫자·라벨은 모두 코드가 계산하고 LLM 은 문장으로만 쓴다.
     *
     * @param strength 0~100. 클수록 사용자가 몰랐을 법하고 바꿀 지점이 분명한 발견이다
     * @param facts    발견의 근거 값 (숫자, 또는 ExpenseMood·ExpenseReason·ExpenseItem·DayOfWeek·문자열)
     */
    public record Finding(FindingType type, int strength, Map<String, Object> facts) {
    }

    public enum FindingType {
        /** 이유에 담긴 의도와 결과가 어긋남 (아끼려던 할인이 더 비쌈, 기분을 풀려던 소비가 후회로 남음) */
        INTENT_BACKFIRE,
        /** 이번 기간 후회의 대부분이 이 패턴에서 나옴 */
        REGRET_CONCENTRATION,
        /** 특정 기분으로 기록한 날의 소비가 대부분 이 패턴 */
        MOOD_TRIGGER,
        /** 이 패턴이 특정 요일이나 주말에 몰림 */
        DAY_TRIGGER,
        /** 한 번은 작지만 모이면 다른 가장 큰 단건 지출보다 큼 */
        SMALL_ADDS_UP,
        /** 기록 수 비중보다 금액 비중이 훨씬 큼 */
        AMOUNT_WEIGHT,
        /** 이 패턴의 후회율이 나머지보다 크게 높음 */
        REGRET_GAP,
        /** 나머지 중 후회가 하나도 없는 이유 (보조 발견: 대비용) */
        SAFE_ZONE,
        /** 위 발견이 없을 때 패턴 자체의 사실 */
        BASIC
    }

    /** 주간은 날짜별, 월간은 주차별(주차의 그 달 안 첫날). */
    public record TrendPoint(LocalDate date, long amount) {
    }

    /**
     * @param totalAmountChangeRate 직전 기간 대비 총액 증감률(%), 소수 첫째 자리 반올림
     */
    public record PreviousSummary(long totalAmount, int regretCount, double totalAmountChangeRate) {
    }

    /** 핵심 패턴을 찾은 규칙. 위에서부터 순서대로 찾고 처음 맞는 것을 쓴다. */
    public enum PatternType {
        /** 같은 항목 안에서 이 이유의 건당 금액이 다른 이유보다 20% 이상 높음 (기획안 7-1 원래 기준) */
        ITEM_AMOUNT,
        /** 이 이유(2건 이상)의 건당 금액이 다른 이유 전체 평균보다 20% 이상 높음 */
        REASON_AMOUNT,
        /** 이 이유(2건 이상)로 쓴 기록의 절반 이상을 후회함 */
        REASON_REGRET,
        /** 2건 이상인 이유 중 가장 자주 쓴 이유 */
        REASON_FREQUENT
    }

    /**
     * 핵심 패턴. ITEM_AMOUNT 는 이유×항목 조합 기준이고, 나머지는 이유 기준이며 item 은 그 이유로 가장 많이 쓴 대표 항목이다.
     *
     * @param comboCount       ITEM_AMOUNT: 이유×항목 건수 / 그 외: 이유 건수
     * @param comboTotal       ITEM_AMOUNT: 이유×항목 합계 / 그 외: 이유 합계
     * @param comboAverage     위 합계의 건당 평균 (원 단위 반올림)
     * @param othersAverage    ITEM_AMOUNT: 같은 항목의 다른 이유 건당 평균 / 그 외: 다른 이유 전체 건당 평균 (없으면 0)
     * @param itemTotal        ITEM_AMOUNT: 해당 항목 전체 합계 / 그 외: 그 이유로 대표 항목에 쓴 합계
     * @param comboRegretCount comboCount 중 후회한 기록 수
     */
    public record Pattern(ExpenseReason reason, ExpenseItem item, int comboCount, long comboTotal,
                          long comboAverage, long othersAverage, long itemTotal, int comboRegretCount,
                          PatternType type) {
    }
}
