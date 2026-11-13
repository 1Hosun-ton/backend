package com.hosunton.backend.domain.mission;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class MissionRecommenderTest {
    @Test void 난이도와_중복_대체() {
        assertThat(MissionRecommender.level(null)).isEqualTo(MissionLevel.BASIC);
        assertThat(MissionRecommender.level(MissionResult.ACHIEVED)).isEqualTo(MissionLevel.ADVANCED);
        assertThat(MissionRecommender.level(MissionResult.FAILED)).isEqualTo(MissionLevel.EASY);
        for (var group : PatternGroup.values()) for (var level : MissionLevel.values()) {
            var items = MissionRecommender.recommend(group, level);
            assertThat(items).hasSize(3);
            assertThat(items.stream().map(i -> i.text()).distinct().count()).isEqualTo(3);
            assertThat(items).extracting(i -> i.optionIndex()).containsExactly(0, 1, 2);
            assertThat(items.getFirst().patternGroup()).isEqualTo(group);
            assertThat(items.getFirst().level()).isEqualTo(level);
        }
        assertThat(MissionRecommender.recommend(PatternGroup.DEFAULT, MissionLevel.BASIC).get(2).patternGroup())
            .isEqualTo(PatternGroup.STRESS_DELIVERY);
    }
}
