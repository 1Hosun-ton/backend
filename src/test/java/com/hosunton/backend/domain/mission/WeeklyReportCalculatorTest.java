package com.hosunton.backend.domain.mission;

import com.hosunton.backend.domain.expense.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
class WeeklyReportCalculatorTest {
    @Test void 기록_없음과_지난주_없음_및_반올림() {
        var start = LocalDate.of(2026, 11, 9);
        var empty = WeeklyReportCalculator.calculate(start, List.of(), List.of(), null);
        assertThat(empty.stressImpulseRatio()).isZero();
        assertThat(empty.previousRegretCount()).isNull();
        assertThat(empty.regretCountChange()).isNull();
        assertThat(empty.highlight()).isNull();
        assertThat(empty.missionCompletionRate()).isNull();
        var stress = Expense.create(1L, start, ExpenseItem.DELIVERY, 100, ExpenseReason.STRESS, true, null, null, Instant.EPOCH);
        var need = Expense.create(1L, start, ExpenseItem.CAFE, 200, ExpenseReason.NEED, false, null, null, Instant.EPOCH);
        var report = WeeklyReportCalculator.calculate(start, List.of(stress, need), List.of(need), null);
        assertThat(report.totalAmount()).isEqualTo(300);
        assertThat(report.stressImpulseRatio()).isEqualTo(33.33);
        assertThat(report.regretCountChange()).isEqualTo(1);
        assertThat(report.reasonBreakdown().getFirst().reason()).isEqualTo(ExpenseReason.NEED);
    }
}
