package com.hosunton.backend.domain.mission;

import com.hosunton.backend.domain.expense.ExpenseRepository;
import com.hosunton.backend.domain.insight.PeriodType;
import com.hosunton.backend.domain.insight.analysis.InsightCalculator;
import com.hosunton.backend.domain.mission.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MissionService {
    private final MissionRepository missions;
    private final MissionRecommendationRepository recommendations;
    private final ExpenseRepository expenses;
    private final MissionJudge judge;
    private final Clock clock;

    @Transactional
    public MissionOverviewResponse getOverview(Long userId) {
        missions.lockUserForOverview(userId);
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, clock.getZone());
        judge.judgeExpired(userId, today, now);
        var active = missions.findByUserIdAndResultIsNull(userId);
        MissionResponse mission = active.or(() -> missions.findFirstByUserIdAndResultAndDifficultyIsNullOrderByStartedOnDescIdDesc(userId, MissionResult.FAILED))
            .map(m -> MissionResponse.from(m, today)).orElse(null);
        long count = expenses.countByUserId(userId);
        if (count < 5) return new MissionOverviewResponse(count, 5, null, null, List.of(), mission);

        LocalDate weekStart = PeriodType.WEEK.startOf(today);
        var current = expenses.findByUserIdAndSpentOnBetween(userId, weekStart, weekStart.plusDays(6));
        var previous = expenses.findByUserIdAndSpentOnBetween(userId, weekStart.minusWeeks(1), weekStart.minusDays(1));
        var weeklyMission = missions.findFirstByUserIdAndStartedOnBetweenOrderByStartedOnDescIdDesc(userId, weekStart, weekStart.plusDays(6)).orElse(null);
        var report = WeeklyReportCalculator.calculate(weekStart, current, previous, weeklyMission);
        if (active.isPresent()) return new MissionOverviewResponse(count, 5, report, null, List.of(), mission);

        var set = recommendations.findFirstByUserIdAndSelectedFalseAndCreatedAtAfterOrderByCreatedAtDescIdDesc(userId, now.minus(7, ChronoUnit.DAYS))
            .orElseGet(() -> {
                var pattern = InsightCalculator.findPattern(current);
                if (pattern == null) {
                    LocalDate monthStart = PeriodType.MONTH.startOf(today);
                    pattern = InsightCalculator.findPattern(expenses.findByUserIdAndSpentOnBetween(userId, monthStart, PeriodType.MONTH.endOf(monthStart)));
                }
                MissionResult last = missions.findFirstByUserIdAndResultIsNotNullOrderByStartedOnDescIdDesc(userId).map(Mission::getResult).orElse(null);
                var level = MissionRecommender.level(last);
                return recommendations.save(MissionRecommendation.create(userId,
                    MissionRecommender.recommend(MissionRecommender.group(pattern), level), level, now));
            });
        return new MissionOverviewResponse(count, 5, report, set.getId(), set.getItems(), mission);
    }
}
