package com.hosunton.backend.domain.insight.analysis;

import com.hosunton.backend.domain.expense.Expense;
import com.hosunton.backend.domain.expense.ExpenseItem;
import com.hosunton.backend.domain.expense.ExpenseReason;
import com.hosunton.backend.domain.insight.PeriodType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static com.hosunton.backend.domain.expense.ExpenseItem.CAFE;
import static com.hosunton.backend.domain.expense.ExpenseItem.DELIVERY;
import static com.hosunton.backend.domain.expense.ExpenseReason.HABIT;
import static com.hosunton.backend.domain.expense.ExpenseReason.NEED;
import static com.hosunton.backend.domain.expense.ExpenseReason.STRESS;
import static org.assertj.core.api.Assertions.assertThat;

/** 패턴 후보 기준(2건 이상 + 20% 이상)의 경계값만 확인한다. */
class InsightCalculatorTest {

    private static final LocalDate START = LocalDate.of(2026, 11, 1);

    private static Expense e(ExpenseItem item, ExpenseReason reason, int amount) {
        return Expense.create(1L, START, item, amount, reason, false, null, null, Instant.EPOCH);
    }

    @Test
    void 정확히_20퍼센트_높으면_패턴이다() {
        // STRESS×배달 평균 12000, 다른 배달 평균 10000 → 정확히 20%
        InsightAnalysis.Pattern p = InsightCalculator.findPattern(List.of(
                e(DELIVERY, STRESS, 12000), e(DELIVERY, STRESS, 12000), e(DELIVERY, NEED, 10000)));

        assertThat(p).isNotNull();
        assertThat(p.reason()).isEqualTo(STRESS);
        assertThat(p.item()).isEqualTo(DELIVERY);
        assertThat(p.comboCount()).isEqualTo(2);
        assertThat(p.itemTotal()).isEqualTo(34000);
    }

    @Test
    void 이십퍼센트_미만이거나_1건이거나_비교_기록이_없으면_패턴이_아니다() {
        // 19.99%
        assertThat(InsightCalculator.findPattern(List.of(
                e(DELIVERY, STRESS, 11999), e(DELIVERY, STRESS, 11999), e(DELIVERY, NEED, 10000)))).isNull();
        // 조합 1건
        assertThat(InsightCalculator.findPattern(List.of(
                e(DELIVERY, STRESS, 50000), e(DELIVERY, NEED, 10000)))).isNull();
        // 같은 항목의 다른 이유 기록 없음
        assertThat(InsightCalculator.findPattern(List.of(
                e(DELIVERY, STRESS, 50000), e(DELIVERY, STRESS, 50000), e(CAFE, NEED, 1000)))).isNull();
    }

    @Test
    void 후보가_여러_개면_조합_합계가_큰_것을_고른다() {
        InsightAnalysis.Pattern p = InsightCalculator.findPattern(List.of(
                e(DELIVERY, STRESS, 20000), e(DELIVERY, STRESS, 20000), e(DELIVERY, NEED, 10000), // 합계 40000
                e(CAFE, HABIT, 30000), e(CAFE, HABIT, 30000), e(CAFE, NEED, 5000)));               // 합계 60000

        assertThat(p.item()).isEqualTo(CAFE);
        assertThat(p.reason()).isEqualTo(HABIT);
    }

    @Test
    void 이유별_비중과_지난_기간_대비_변화를_계산한다() {
        InsightAnalysis a = InsightCalculator.analyze(PeriodType.MONTH, START,
                List.of(e(DELIVERY, STRESS, 30000), e(CAFE, NEED, 10000)),
                List.of(e(DELIVERY, STRESS, 10000), e(CAFE, NEED, 10000)));

        assertThat(a.totalAmount()).isEqualTo(40000);
        InsightAnalysis.ReasonShare stress = a.reasonBreakdown().getFirst();
        assertThat(stress.reason()).isEqualTo(STRESS);
        assertThat(stress.ratio()).isEqualTo(75.0);
        assertThat(stress.previousRatio()).isEqualTo(50.0);
        assertThat(stress.changePercentagePoint()).isEqualTo(25.0);
        assertThat(a.previous().totalAmountChangeRate()).isEqualTo(100.0);
        assertThat(a.trend()).hasSize(6); // 2026년 11월은 월요일 기준 6개 주차
    }
}
