package com.hosunton.backend.domain.insight;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * insights 테이블 캐시 저장·무효화 (기획안 7-5).
 */
@Service
@RequiredArgsConstructor
public class InsightCacheService {

    private final InsightRepository insightRepository;

    /**
     * 별도 트랜잭션으로 저장한다. 동시 요청으로 UNIQUE(user_id, period_type, period_start)에 걸리면
     * 이 트랜잭션만 롤백되고, 호출한 쪽은 예외를 무시하고 이미 계산한 결과를 그대로 응답한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(Long userId, PeriodType type, LocalDate periodStart, String resultJson, Instant now) {
        insightRepository.saveAndFlush(Insight.create(userId, type, periodStart, resultJson, now));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void evict(Long id) {
        insightRepository.deleteById(id);
    }

    /**
     * 기록이 추가·삭제된 날짜가 속한 주·월, 그리고 그 기간을 직전 기간으로 비교하는 다음 주·월의 저장 결과를 지운다.
     * 기록 저장·삭제와 같은 트랜잭션에서 호출한다.
     */
    @Transactional
    public void invalidate(Long userId, LocalDate spentOn) {
        LocalDate week = PeriodType.WEEK.startOf(spentOn);
        LocalDate month = PeriodType.MONTH.startOf(spentOn);
        insightRepository.deleteForPeriods(userId,
                List.of(week, PeriodType.WEEK.nextStart(week)),
                List.of(month, PeriodType.MONTH.nextStart(month)));
    }
}
