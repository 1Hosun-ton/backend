package com.hosunton.backend.domain.insight;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;

public interface InsightRepository extends JpaRepository<Insight, Long> {

    Optional<Insight> findByUserIdAndPeriodTypeAndPeriodStart(Long userId, PeriodType periodType, LocalDate periodStart);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            delete from Insight i
            where i.userId = :userId
              and ((i.periodType = com.hosunton.backend.domain.insight.PeriodType.WEEK and i.periodStart in :weekStarts)
                or (i.periodType = com.hosunton.backend.domain.insight.PeriodType.MONTH and i.periodStart in :monthStarts))
            """)
    int deleteForPeriods(@Param("userId") Long userId,
                         @Param("weekStarts") Collection<LocalDate> weekStarts,
                         @Param("monthStarts") Collection<LocalDate> monthStarts);
}
