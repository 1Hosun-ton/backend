package com.hosunton.backend.domain.insight;

import com.hosunton.backend.domain.expense.Expense;
import com.hosunton.backend.domain.expense.ExpenseRepository;
import com.hosunton.backend.domain.insight.ai.InsightNarrator;
import com.hosunton.backend.domain.insight.analysis.InsightAnalysis;
import com.hosunton.backend.domain.insight.analysis.InsightCalculator;
import com.hosunton.backend.domain.insight.dto.InsightResponse;
import com.hosunton.backend.domain.insight.dto.InsightResponse.AnalysisSource;
import com.hosunton.backend.domain.insight.dto.InsightResponse.InsightStatus;
import com.hosunton.backend.global.exception.BusinessException;
import com.hosunton.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * GET /api/v1/insights.
 * LLM 호출이 DB 트랜잭션을 오래 잡지 않도록 메서드 전체를 트랜잭션으로 묶지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InsightService {

    public static final int REQUIRED_EXPENSE_COUNT = 5;

    private final ExpenseRepository expenseRepository;
    private final InsightRepository insightRepository;
    private final InsightCacheService insightCacheService;
    private final InsightNarrator insightNarrator;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public InsightResponse getInsight(Long userId, PeriodType requestedType, LocalDate requestedStart) {
        PeriodType type = requestedType != null ? requestedType : PeriodType.MONTH;
        LocalDate currentStart = type.startOf(LocalDate.now(clock));
        LocalDate start = requestedStart != null ? requestedStart : currentStart;
        if (!type.isPeriodStart(start) || start.isAfter(currentStart)) {
            // 주간은 월요일, 월간은 1일만 허용하고 미래 기간은 조회할 수 없다.
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        LocalDate end = type.endOf(start);
        InsightResponse.Period period = new InsightResponse.Period(type, start, end);

        long cumulativeCount = expenseRepository.countByUserId(userId);
        if (cumulativeCount < REQUIRED_EXPENSE_COUNT) {
            return empty(InsightStatus.INSUFFICIENT_DATA, cumulativeCount, period);
        }

        Optional<InsightResponse> cached = readCache(userId, type, start);
        if (cached.isPresent()) {
            return cached.get().withCumulativeExpenseCount(cumulativeCount);
        }

        List<Expense> current = expenseRepository.findByUserIdAndSpentOnBetween(userId, start, end);
        if (current.isEmpty()) {
            return empty(InsightStatus.NO_EXPENSES, cumulativeCount, period);
        }
        LocalDate previousStart = type.previousStart(start);
        List<Expense> previous = expenseRepository.findByUserIdAndSpentOnBetween(
                userId, previousStart, type.endOf(previousStart));

        InsightAnalysis analysis = InsightCalculator.analyze(type, start, current, previous);
        if (!analysis.hasPattern()) {
            return statsOnly(cumulativeCount, period, analysis);
        }

        InsightNarrator.Narration narration = insightNarrator.narrate(type, start, end, analysis);
        InsightResponse response = ready(cumulativeCount, period, analysis, narration);
        if (narration.fromAi()) {
            saveCache(userId, type, start, response);
        }
        return response;
    }

    // ===== 캐시 =====

    private Optional<InsightResponse> readCache(Long userId, PeriodType type, LocalDate start) {
        return insightRepository.findByUserIdAndPeriodTypeAndPeriodStart(userId, type, start)
                .flatMap(insight -> {
                    try {
                        return Optional.of(jsonMapper.readValue(insight.getResult(), InsightResponse.class));
                    } catch (JacksonException e) {
                        // 응답 구조가 바뀌어 읽을 수 없는 옛 결과는 지우고 새로 만든다.
                        log.warn("Discarding unreadable insight cache id={}", insight.getId());
                        insightCacheService.evict(insight.getId());
                        return Optional.empty();
                    }
                });
    }

    private void saveCache(Long userId, PeriodType type, LocalDate start, InsightResponse response) {
        try {
            insightCacheService.save(userId, type, start, jsonMapper.writeValueAsString(response), clock.instant());
        } catch (DataIntegrityViolationException e) {
            log.info("Insight already cached by a concurrent request (userId={}, {} {})", userId, type, start);
        }
    }

    // ===== 응답 조립 =====

    private InsightResponse empty(InsightStatus status, long cumulativeCount, InsightResponse.Period period) {
        return new InsightResponse(status, cumulativeCount, REQUIRED_EXPENSE_COUNT, period, 0, 0, null,
                List.of(), List.of(), null, null, null, AnalysisSource.NONE);
    }

    private InsightResponse statsOnly(long cumulativeCount, InsightResponse.Period period, InsightAnalysis a) {
        return new InsightResponse(InsightStatus.NO_PATTERN, cumulativeCount, REQUIRED_EXPENSE_COUNT, period,
                a.totalAmount(), a.regretCount(), null, reasonShares(a), trend(a),
                null, null, null, AnalysisSource.NONE);
    }

    private InsightResponse ready(long cumulativeCount, InsightResponse.Period period, InsightAnalysis a,
                                  InsightNarrator.Narration narration) {
        InsightAnalysis.Pattern p = a.pattern();
        InsightResponse.KeyInsight keyInsight = new InsightResponse.KeyInsight(
                narration.text().insight(),
                new InsightResponse.Evidence(p.reason(), p.comboCount(), p.item(), p.itemTotal()));
        return new InsightResponse(InsightStatus.READY, cumulativeCount, REQUIRED_EXPENSE_COUNT, period,
                a.totalAmount(), a.regretCount(), keyInsight, reasonShares(a), trend(a),
                narration.text().comparison(), narration.text().suggestion(),
                narration.text().questionForReflection(),
                narration.fromAi() ? AnalysisSource.AI : AnalysisSource.FALLBACK);
    }

    private static List<InsightResponse.ReasonShare> reasonShares(InsightAnalysis a) {
        return a.reasonBreakdown().stream()
                .map(s -> new InsightResponse.ReasonShare(s.reason(), s.amount(), s.ratio(),
                        s.previousRatio(), s.changePercentagePoint()))
                .toList();
    }

    private static List<InsightResponse.TrendPoint> trend(InsightAnalysis a) {
        return a.trend().stream().map(t -> new InsightResponse.TrendPoint(t.date(), t.amount())).toList();
    }
}
