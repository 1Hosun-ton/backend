package com.hosunton.backend.domain.insight.ai;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** AI 문장 검증: 통계에 없는 숫자 금지, 훈계·격식체 금지, 끝맺음 확인. */
class InsightNarratorValidationTest {

    private final InsightNarrator narrator = new InsightNarrator(null, null);
    private static final Set<String> NUMBERS = Set.of("11.4");

    private static InsightText text(String insight, String comparison, String suggestion, String question) {
        return new InsightText(insight, comparison, suggestion, question);
    }

    private static final String INSIGHT = "스트레스를 풀려고 시킨 배달 음식은 다른 배달 음식보다 건당 금액이 높아요.";
    private static final String SUGGESTION = "배달 앱을 열기 전에 지금 기분을 적어 보는 건 어때요?";
    private static final String QUESTION = "후회한 배달과 괜찮았던 배달은 어떤 점이 달랐나요?";

    @Test
    void 규칙을_지킨_문장은_통과한다() {
        InsightText result = narrator.validate(
                text(INSIGHT, "지난달보다 스트레스 해소 소비 비중이 11.4%p 늘었어요", SUGGESTION, QUESTION), true, NUMBERS);
        assertThat(result.comparison()).isEqualTo("지난달보다 스트레스 해소 소비 비중이 11.4%p 늘었어요");
    }

    @Test
    void 숫자_위치_훈계_격식체_끝맺음을_어기면_거부한다() {
        assertThatThrownBy(() -> narrator.validate(
                text("배달 음식이 99% 높아요.", "", SUGGESTION, QUESTION), false, NUMBERS)) // 통계에 없는 숫자
                .isInstanceOf(InsightGenerationException.class);
        assertThatThrownBy(() -> narrator.validate(
                text(INSIGHT, "", "배달은 반드시 줄이세요?", QUESTION), false, NUMBERS))
                .isInstanceOf(InsightGenerationException.class);
        assertThatThrownBy(() -> narrator.validate(
                text("배달 음식 지출이 높습니다.", "", SUGGESTION, QUESTION), false, NUMBERS))
                .isInstanceOf(InsightGenerationException.class);
        assertThatThrownBy(() -> narrator.validate(
                text(INSIGHT, "", "배달 앱을 열기 전에 기분을 적어 보세요.", QUESTION), false, NUMBERS))
                .isInstanceOf(InsightGenerationException.class);
    }
}
