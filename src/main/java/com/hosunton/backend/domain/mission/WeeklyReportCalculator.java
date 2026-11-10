package com.hosunton.backend.domain.mission;

import com.hosunton.backend.domain.expense.*;
import com.hosunton.backend.domain.insight.*;
import com.hosunton.backend.domain.insight.analysis.InsightCalculator;
import com.hosunton.backend.domain.mission.dto.WeeklyReportResponse;
import java.math.*;
import java.time.LocalDate;
import java.util.List;
public final class WeeklyReportCalculator {
    private WeeklyReportCalculator() {}
    public static WeeklyReportResponse calculate(LocalDate weekStart, List<Expense> current,
                                                  List<Expense> previous, Mission weeklyMission) {
        var stats = InsightCalculator.analyze(PeriodType.WEEK, weekStart, current, previous);
        long stressImpulse = current.stream().filter(e -> e.getReason() == ExpenseReason.STRESS || e.getReason() == ExpenseReason.IMPULSE)
            .mapToLong(Expense::getAmount).sum();
        var shares = stats.reasonBreakdown().stream()
            .map(s -> new WeeklyReportResponse.ReasonShare(s.reason(), s.amount(), s.ratio())).toList();
        String highlight = shares.isEmpty() ? null : "이번 주는 '" + InsightLabels.of(shares.getFirst().reason()) + "' 소비 비중이 가장 높게 나타났어요.";
        Integer previousRegret = stats.previous() == null ? null : stats.previous().regretCount();
        return new WeeklyReportResponse(weekStart, weekStart.plusDays(6), highlight, stats.totalAmount(), stats.regretCount(),
            previousRegret, previousRegret == null ? null : stats.regretCount() - previousRegret,
            percent(stressImpulse, stats.totalAmount()), shares, weeklyMission == null ? null : percent(weeklyMission.getDoneDays(), 7));
    }
    private static double percent(long part, long total) {
        return total == 0 ? 0.0 : BigDecimal.valueOf(part).multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP).doubleValue();
    }
}
