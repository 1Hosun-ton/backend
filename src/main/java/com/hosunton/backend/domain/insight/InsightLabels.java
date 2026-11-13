package com.hosunton.backend.domain.insight;

import com.hosunton.backend.domain.expense.ExpenseItem;
import com.hosunton.backend.domain.expense.ExpenseReason;

/**
 * LLM 입력과 대체 문장에 쓰는 화면 표시 이름 (기획안 8-1 enum 표 기준).
 */
public final class InsightLabels {

    private InsightLabels() {
    }

    public static String of(ExpenseItem item) {
        return switch (item) {
            case FOOD -> "식비";
            case DELIVERY -> "배달 음식";
            case CAFE -> "카페";
            case SHOPPING -> "쇼핑";
            case TRANSPORT -> "교통비";
            case ETC -> "기타";
        };
    }

    public static String of(ExpenseReason reason) {
        return switch (reason) {
            case NEED -> "필요해서";
            case STRESS -> "스트레스 해소";
            case DISCOUNT -> "할인";
            case IMPULSE -> "충동";
            case REWARD -> "보상";
            case HABIT -> "습관";
        };
    }

    public static String of(PeriodType type) {
        return switch (type) {
            case WEEK -> "주간";
            case MONTH -> "월간";
        };
    }

    /** "지난주" / "지난달" */
    public static String previousPeriod(PeriodType type) {
        return switch (type) {
            case WEEK -> "지난주";
            case MONTH -> "지난달";
        };
    }
}
