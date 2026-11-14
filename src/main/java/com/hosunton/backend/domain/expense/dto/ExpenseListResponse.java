package com.hosunton.backend.domain.expense.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/**
 * 소비 기록 목록. summary는 월 조회일 때만 채우고(그 달 전체 기준, 필터 미적용), 기간 조회면 null.
 */
public record ExpenseListResponse(
        Period period,
        Summary summary,
        int totalCount,
        List<ExpenseResponse> expenses
) {

    @Schema(name = "ExpensePeriod")
    public record Period(LocalDate from, LocalDate to) {
    }

    @Schema(name = "ExpenseSummary")
    public record Summary(long totalAmount, long dailyAverage, long weeklyAverage) {
    }
}
