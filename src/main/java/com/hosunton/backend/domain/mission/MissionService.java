package com.hosunton.backend.domain.mission;

import com.hosunton.backend.domain.expense.ExpenseRepository;
import com.hosunton.backend.domain.insight.PeriodType;
import com.hosunton.backend.domain.insight.analysis.InsightCalculator;
import com.hosunton.backend.domain.mission.dto.*;
import com.hosunton.backend.global.exception.BusinessException;
import com.hosunton.backend.global.exception.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
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
        // 진행 중 미션이 없으면 가장 최근 종료 미션(달성·미달성)을 보여 준다. 새 미션을 시작하면 진행 중 미션으로 바뀐다.
        MissionResponse mission = active.or(() -> missions.findFirstByUserIdAndResultIsNotNullOrderByStartedOnDescIdDesc(userId))
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

    @Transactional
    public MissionStartResponse start(Long userId, MissionStartRequest request) {
        if (request == null || request.recommendationSetId() == null || request.optionIndex() == null
                || request.optionIndex() < 0 || request.optionIndex() > 2) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        // overview와 같은 잠금 순서로 추천 생성/선택 및 중복 시작을 직렬화한다.
        missions.lockUserForOverview(userId);
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, clock.getZone());
        judge.judgeExpired(userId, today, now);
        var recommendation = recommendations.findByIdAndUserId(request.recommendationSetId(), userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        Instant after = now.minus(7, ChronoUnit.DAYS);
        if (recommendation.isSelected() || !recommendation.getCreatedAt().isAfter(after)) {
            throw new BusinessException(ErrorCode.RECOMMENDATION_EXPIRED);
        }
        if (missions.findByUserIdAndResultIsNull(userId).isPresent()) {
            throw new BusinessException(ErrorCode.MISSION_ALREADY_IN_PROGRESS);
        }
        var item = recommendation.getItems().stream().filter(i -> i.optionIndex() == request.optionIndex())
                .findFirst().orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR));
        if (recommendations.selectOnce(recommendation.getId(), userId, after) == 0) {
            throw new BusinessException(ErrorCode.RECOMMENDATION_EXPIRED);
        }
        try {
            var mission = missions.saveAndFlush(Mission.create(userId, recommendation.getId(),
                    item.patternGroup(), item.level(), item.text(), today, now));
            return MissionStartResponse.from(mission);
        } catch (DataIntegrityViolationException e) {
            // 다른 무결성 오류는 숨기지 않고 진행 미션 부분 UNIQUE 충돌만 변환한다.
            for (Throwable cause = e; cause != null; cause = cause.getCause()) {
                if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                        && "uk_missions_user_in_progress".equals(violation.getConstraintName())) {
                    throw new BusinessException(ErrorCode.MISSION_ALREADY_IN_PROGRESS);
                }
            }
            throw e;
        }
    }

    @Transactional
    public MissionCheckResponse check(Long userId, Long missionId, MissionCheckRequest request) {
        String memo = request == null ? null : request.memo();
        // 빈 메모·공백 메모는 입력하지 않은 것으로 보고 기존 메모를 유지한다.
        if (memo != null && memo.isBlank()) memo = null;
        if (memo != null && memo.length() > 100) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        ownedMission(userId, missionId);
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, clock.getZone());
        judge.judgeExpired(userId, today, now);
        int updated = missions.checkToday(missionId, userId, today, today.minusDays(6), memo);
        Mission mission = ownedMission(userId, missionId);
        if (updated == 0) {
            throw new BusinessException(today.equals(mission.getLastCheckedOn())
                    ? ErrorCode.ALREADY_CHECKED_TODAY : ErrorCode.MISSION_NOT_ACTIVE);
        }
        return MissionCheckResponse.from(mission);
    }

    @Transactional
    public MissionDifficultyResponse updateDifficulty(Long userId, Long missionId, MissionDifficultyRequest request) {
        if (request == null || request.difficulty() == null) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        ownedMission(userId, missionId);
        Instant now = clock.instant();
        judge.judgeExpired(userId, LocalDate.ofInstant(now, clock.getZone()), now);
        Mission mission = ownedMission(userId, missionId);
        if (mission.getResult() != MissionResult.FAILED) throw new BusinessException(ErrorCode.MISSION_NOT_FAILED);
        mission.updateDifficulty(request.difficulty());
        return MissionDifficultyResponse.from(mission);
    }

    private Mission ownedMission(Long userId, Long missionId) {
        return missions.findByIdAndUserId(missionId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }
}
