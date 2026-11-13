package com.hosunton.backend.domain.insight.ai;

import com.hosunton.backend.domain.expense.ExpenseItem;
import com.hosunton.backend.domain.expense.ExpenseReason;
import com.hosunton.backend.domain.insight.PeriodType;
import com.hosunton.backend.domain.insight.analysis.InsightAnalysis;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.hosunton.backend.domain.expense.ExpenseItem.DELIVERY;
import static com.hosunton.backend.domain.expense.ExpenseItem.TRANSPORT;
import static com.hosunton.backend.domain.expense.ExpenseReason.NEED;
import static com.hosunton.backend.domain.expense.ExpenseReason.STRESS;
import static org.assertj.core.api.Assertions.assertThat;

/** AI 실패 때 화면에 나가는 대체 문장을 글자 그대로 확인한다. */
class InsightNarratorFallbackTest {

    private final InsightNarrator narrator = new InsightNarrator(null, null);

    private static InsightAnalysis analysis(ExpenseReason reason, ExpenseItem item,
                                            InsightAnalysis.PreviousSummary previous) {
        return new InsightAnalysis(196650, 15, 4, List.of(), List.of(), previous,
                new InsightAnalysis.Pattern(reason, item, 3, 73500, 24500, 11750, 97000));
    }

    @Test
    void 패턴_그룹_문장과_전체_지출_증가를_쓴다() {
        InsightText text = narrator.fallback(PeriodType.MONTH,
                analysis(STRESS, DELIVERY, new InsightAnalysis.PreviousSummary(193000, 5, 1.9)));

        assertThat(text.insight()).isEqualTo("스트레스 해소로 쓴 배달 음식 지출은 다른 배달 음식 지출보다 건당 금액이 높아요.");
        assertThat(text.comparison()).isEqualTo("지난달보다 전체 지출이 1.9% 늘었어요");
        assertThat(text.suggestion()).isEqualTo("배달 앱을 열기 전에 물 한 잔 마시고 5분 뒤에 결정해 보는 건 어때요?");
        assertThat(text.questionForReflection()).isEqualTo("스트레스나 보상이 필요했던 날, 기분이 나아졌던 다른 방법이 있었나요?");
    }

    @Test
    void 감소는_부호를_떼고_정수_증감률은_소수점_없이_쓴다() {
        InsightText text = narrator.fallback(PeriodType.WEEK,
                analysis(NEED, TRANSPORT, new InsightAnalysis.PreviousSummary(245000, 4, -20.0)));

        assertThat(text.insight()).isEqualTo("필요해서 쓴 교통비 지출은 다른 교통비 지출보다 건당 금액이 높아요.");
        assertThat(text.comparison()).isEqualTo("지난주보다 전체 지출이 20% 줄었어요");
        assertThat(text.suggestion()).isEqualTo("결제하기 전에 이번 주 남은 예산을 한 번 확인해 보는 건 어때요?");
    }

    @Test
    void 증감이_없거나_직전_기간이_없을_때() {
        assertThat(narrator.fallback(PeriodType.WEEK,
                analysis(STRESS, DELIVERY, new InsightAnalysis.PreviousSummary(196650, 4, 0.0))).comparison())
                .isEqualTo("지난주와 전체 지출이 같아요");
        assertThat(narrator.fallback(PeriodType.MONTH, analysis(STRESS, DELIVERY, null)).comparison()).isNull();
    }
}
