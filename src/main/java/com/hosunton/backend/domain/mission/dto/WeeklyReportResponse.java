package com.hosunton.backend.domain.mission.dto;

import com.hosunton.backend.domain.expense.ExpenseReason;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
public record WeeklyReportResponse(LocalDate weekStart, LocalDate weekEnd, String highlight,
        long totalAmount, int regretCount, Integer previousRegretCount, Integer regretCountChange,
        double stressImpulseRatio, List<ReasonShare> reasonBreakdown, Double missionCompletionRate) {
    @Schema(name = "WeeklyReasonShare")
    public record ReasonShare(ExpenseReason reason, long amount, double ratio) {}
}
