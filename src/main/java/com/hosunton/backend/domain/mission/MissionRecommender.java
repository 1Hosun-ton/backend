package com.hosunton.backend.domain.mission;

import com.hosunton.backend.domain.insight.analysis.InsightAnalysis.Pattern;
import com.hosunton.backend.domain.mission.dto.RecommendationItem;
import java.util.*;
public final class MissionRecommender {
    private MissionRecommender() {}
    public static MissionLevel level(MissionResult previous) {
        return previous == null ? MissionLevel.BASIC : previous == MissionResult.ACHIEVED ? MissionLevel.ADVANCED : MissionLevel.EASY;
    }
    public static PatternGroup group(Pattern p) {
        if (p == null) return PatternGroup.DEFAULT;
        return switch (p.item()) {
            case DELIVERY -> switch (p.reason()) {
                case STRESS, REWARD -> PatternGroup.STRESS_DELIVERY;
                default -> PatternGroup.DEFAULT;
            };
            case SHOPPING -> switch (p.reason()) {
                case IMPULSE, DISCOUNT -> PatternGroup.IMPULSE_SHOPPING;
                default -> PatternGroup.DEFAULT;
            };
            case CAFE, FOOD -> p.reason() == com.hosunton.backend.domain.expense.ExpenseReason.HABIT ? PatternGroup.HABIT_CAFE_FOOD : PatternGroup.DEFAULT;
            default -> PatternGroup.DEFAULT;
        };
    }
    public static List<RecommendationItem> recommend(PatternGroup group, MissionLevel level) {
        List<RecommendationItem> items = new ArrayList<>();
        add(items, group, level);
        add(items, group, level == MissionLevel.BASIC ? MissionLevel.EASY : MissionLevel.BASIC);
        add(items, PatternGroup.DEFAULT, MissionLevel.BASIC);
        return List.copyOf(items);
    }
    private static void add(List<RecommendationItem> items, PatternGroup group, MissionLevel level) {
        if (contains(items, group, level)) {
            group = Arrays.stream(PatternGroup.values()).filter(g -> !contains(items, g, MissionLevel.BASIC)).findFirst().orElseThrow();
            level = MissionLevel.BASIC;
        }
        items.add(new RecommendationItem(items.size(), MissionCatalog.text(group, level), group, level));
    }
    private static boolean contains(List<RecommendationItem> items, PatternGroup group, MissionLevel level) {
        return items.stream().anyMatch(i -> i.patternGroup() == group && i.level() == level);
    }
}
