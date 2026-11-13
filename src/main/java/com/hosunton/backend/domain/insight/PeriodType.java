package com.hosunton.backend.domain.insight;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/**
 * 인사이트 조회 기간. 주간은 월요일~일요일, 월간은 1일~말일 (KST).
 */
public enum PeriodType {
    WEEK,
    MONTH;

    /** date가 속한 기간의 시작일. */
    public LocalDate startOf(LocalDate date) {
        return switch (this) {
            case WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> date.withDayOfMonth(1);
        };
    }

    public LocalDate endOf(LocalDate periodStart) {
        return switch (this) {
            case WEEK -> periodStart.plusDays(6);
            case MONTH -> periodStart.withDayOfMonth(periodStart.lengthOfMonth());
        };
    }

    public LocalDate previousStart(LocalDate periodStart) {
        return switch (this) {
            case WEEK -> periodStart.minusWeeks(1);
            case MONTH -> periodStart.minusMonths(1);
        };
    }

    public LocalDate nextStart(LocalDate periodStart) {
        return switch (this) {
            case WEEK -> periodStart.plusWeeks(1);
            case MONTH -> periodStart.plusMonths(1);
        };
    }

    public boolean isPeriodStart(LocalDate date) {
        return startOf(date).equals(date);
    }
}
