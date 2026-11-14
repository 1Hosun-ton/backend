package com.hosunton.backend.domain.insight.analysis;

import com.hosunton.backend.domain.expense.Expense;
import com.hosunton.backend.domain.expense.ExpenseItem;
import com.hosunton.backend.domain.expense.ExpenseMood;
import com.hosunton.backend.domain.expense.ExpenseReason;
import com.hosunton.backend.domain.insight.PeriodType;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static com.hosunton.backend.domain.expense.ExpenseMood.DEPRESSED;
import static com.hosunton.backend.domain.expense.ExpenseMood.NORMAL;
import static com.hosunton.backend.domain.expense.ExpenseItem.CAFE;
import static com.hosunton.backend.domain.expense.ExpenseItem.DELIVERY;
import static com.hosunton.backend.domain.expense.ExpenseItem.FOOD;
import static com.hosunton.backend.domain.expense.ExpenseItem.SHOPPING;
import static com.hosunton.backend.domain.expense.ExpenseReason.DISCOUNT;
import static com.hosunton.backend.domain.expense.ExpenseReason.HABIT;
import static com.hosunton.backend.domain.expense.ExpenseReason.IMPULSE;
import static com.hosunton.backend.domain.expense.ExpenseReason.NEED;
import static com.hosunton.backend.domain.expense.ExpenseReason.REWARD;
import static com.hosunton.backend.domain.expense.ExpenseReason.STRESS;
import static org.assertj.core.api.Assertions.assertThat;

/** 패턴 후보 기준(2건 이상 + 20% 이상)의 경계값과 발견 계산을 확인한다. */
class InsightCalculatorTest {

    private static final LocalDate START = LocalDate.of(2026, 11, 1);

    private static Expense e(ExpenseItem item, ExpenseReason reason, int amount) {
        return Expense.create(1L, START, item, amount, reason, false, null, null, Instant.EPOCH);
    }

    private static Expense regret(ExpenseItem item, ExpenseReason reason, int amount) {
        return Expense.create(1L, START, item, amount, reason, true, null, null, Instant.EPOCH);
    }

    private static Expense on(int day, ExpenseItem item, ExpenseReason reason, int amount, boolean regret, ExpenseMood mood) {
        return Expense.create(1L, LocalDate.of(2026, 6, day), item, amount, reason, regret, mood, null, Instant.EPOCH);
    }

    private static InsightAnalysis.Finding find(List<InsightAnalysis.Finding> findings, InsightAnalysis.FindingType type) {
        return findings.stream().filter(f -> f.type() == type).findFirst().orElse(null);
    }

    @Test
    void 후회가_몰린_곳과_기분_요일_계기를_찾는다() {
        // 2026-06-05·12·19 는 금요일. 충동 쇼핑 3건 모두 후회, 우울 2건. 필요해서 3건은 후회 없음.
        List<Expense> current = List.of(
                on(5, SHOPPING, IMPULSE, 32000, true, DEPRESSED), on(12, SHOPPING, IMPULSE, 27000, true, DEPRESSED),
                on(19, SHOPPING, IMPULSE, 41000, true, null),
                on(2, FOOD, NEED, 9000, false, null), on(9, FOOD, NEED, 8000, false, NORMAL), on(16, FOOD, NEED, 9500, false, null));
        InsightAnalysis.Pattern p = InsightCalculator.findKeyPattern(current);

        List<InsightAnalysis.Finding> f = InsightCalculator.findings(p, current);

        assertThat(f.getFirst().type()).isEqualTo(InsightAnalysis.FindingType.REGRET_CONCENTRATION);
        assertThat(f.getLast().type()).isEqualTo(InsightAnalysis.FindingType.BASIC);
        assertThat(find(f, InsightAnalysis.FindingType.REGRET_CONCENTRATION).facts())
                .containsEntry("regretCount", 3).containsEntry("totalRegretCount", 3);
        assertThat(find(f, InsightAnalysis.FindingType.MOOD_TRIGGER).facts())
                .containsEntry("mood", DEPRESSED).containsEntry("moodCount", 2);
        assertThat(find(f, InsightAnalysis.FindingType.DAY_TRIGGER).facts()).containsEntry("day", DayOfWeek.FRIDAY);
        assertThat(find(f, InsightAnalysis.FindingType.SAFE_ZONE).facts()).containsEntry("reason", NEED).containsEntry("count", 3);
    }

    @Test
    void 할인이_더_비싸거나_작은_금액이_모이면_발견이다() {
        List<Expense> discount = List.of(
                on(6, SHOPPING, DISCOUNT, 25000, false, NORMAL), on(14, SHOPPING, DISCOUNT, 12500, false, NORMAL),
                on(21, CAFE, DISCOUNT, 5000, false, null),
                on(3, FOOD, NEED, 8000, false, null), on(10, CAFE, HABIT, 5000, false, null),
                on(17, DELIVERY, STRESS, 9000, false, null), on(24, FOOD, NEED, 6500, false, null));
        InsightAnalysis.Finding backfire = InsightCalculator.findings(InsightCalculator.findKeyPattern(discount), discount).getFirst();
        assertThat(backfire.type()).isEqualTo(InsightAnalysis.FindingType.INTENT_BACKFIRE);
        assertThat(backfire.facts()).containsEntry("highestAverageAmongReasons", true).containsEntry("averageRatio", 2.0);
        // 보통 기분은 계기로 쓰지 않는다
        assertThat(find(InsightCalculator.findings(InsightCalculator.findKeyPattern(discount), discount),
                InsightAnalysis.FindingType.MOOD_TRIGGER)).isNull();

        // 습관 카페 5건 23,500원 ≥ 나머지 가장 큰 한 번 18,000원
        List<Expense> habit = List.of(
                on(4, CAFE, HABIT, 4500, false, null), on(6, CAFE, HABIT, 4800, false, null), on(11, CAFE, HABIT, 4500, false, null),
                on(13, CAFE, HABIT, 5200, false, null), on(18, CAFE, HABIT, 4500, false, null),
                on(9, SHOPPING, NEED, 18000, false, null), on(23, DELIVERY, REWARD, 16000, false, null));
        InsightAnalysis.Finding small = InsightCalculator.findings(InsightCalculator.findKeyPattern(habit), habit).getFirst();
        assertThat(small.type()).isEqualTo(InsightAnalysis.FindingType.SMALL_ADDS_UP);
        assertThat(small.facts()).containsEntry("total", 23500L).containsEntry("biggestOtherAmount", 18000);
    }

    @Test
    void 항목_패턴이_없으면_이유_금액_후회_빈도_순으로_찾는다() {
        // ② 할인 2건(쇼핑 25000, 카페 5000) 평균 15000 ≥ 다른 이유 평균 6000 × 1.2 → 대표 항목은 쇼핑
        InsightAnalysis.Pattern amount = InsightCalculator.findKeyPattern(List.of(
                e(SHOPPING, DISCOUNT, 25000), e(CAFE, DISCOUNT, 5000),
                e(FOOD, NEED, 8000), e(DELIVERY, STRESS, 6000), e(CAFE, HABIT, 4000)));
        assertThat(amount.type()).isEqualTo(InsightAnalysis.PatternType.REASON_AMOUNT);
        assertThat(amount.reason()).isEqualTo(DISCOUNT);
        assertThat(amount.item()).isEqualTo(SHOPPING);
        assertThat(amount.comboCount()).isEqualTo(2);

        // ③ 충동 2건 모두 후회, 금액은 평균 이하
        InsightAnalysis.Pattern regretPattern = InsightCalculator.findKeyPattern(List.of(
                regret(SHOPPING, IMPULSE, 5000), regret(CAFE, IMPULSE, 5000),
                e(FOOD, NEED, 9000), e(DELIVERY, STRESS, 9000), e(CAFE, HABIT, 9000)));
        assertThat(regretPattern.type()).isEqualTo(InsightAnalysis.PatternType.REASON_REGRET);
        assertThat(regretPattern.reason()).isEqualTo(IMPULSE);

        // ④ 금액·후회 조건 없이 필요해서 2건이 가장 잦음
        InsightAnalysis.Pattern frequent = InsightCalculator.findKeyPattern(List.of(
                e(FOOD, NEED, 5000), e(CAFE, NEED, 5000),
                e(DELIVERY, STRESS, 9000), e(SHOPPING, REWARD, 9000), e(CAFE, HABIT, 9000)));
        assertThat(frequent.type()).isEqualTo(InsightAnalysis.PatternType.REASON_FREQUENT);
        assertThat(frequent.reason()).isEqualTo(NEED);

        // 이유가 모두 1건씩이면 패턴 없음
        assertThat(InsightCalculator.findKeyPattern(List.of(
                e(FOOD, NEED, 5000), e(DELIVERY, STRESS, 9000), e(SHOPPING, REWARD, 9000),
                e(CAFE, HABIT, 9000), e(SHOPPING, IMPULSE, 9000)))).isNull();
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
