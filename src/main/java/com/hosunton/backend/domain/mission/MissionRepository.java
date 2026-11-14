package com.hosunton.backend.domain.mission;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.*;
import java.util.Optional;

public interface MissionRepository extends JpaRepository<Mission, Long> {
    // 추천이 아직 없는 최초 조회도 사용자별로 직렬화한다. 스키마 변경 없이 중복 생성을 방지한다.
    @Query(value = "select id from users where id = :userId for update", nativeQuery = true)
    Long lockUserForOverview(@Param("userId") Long userId);

    Optional<Mission> findByIdAndUserId(Long id, Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        update Mission m set m.doneDays = m.doneDays + 1, m.lastCheckedOn = :today,
            m.memo = coalesce(:memo, m.memo)
        where m.id = :id and m.userId = :userId and m.result is null
            and m.startedOn between :earliestStart and :today and m.doneDays < 7
            and (m.lastCheckedOn is null or m.lastCheckedOn < :today)
        """)
    int checkToday(@Param("id") Long id, @Param("userId") Long userId,
                   @Param("today") LocalDate today, @Param("earliestStart") LocalDate earliestStart,
                   @Param("memo") String memo);

    Optional<Mission> findByUserIdAndResultIsNull(Long userId);
    Optional<Mission> findFirstByUserIdAndResultIsNotNullOrderByStartedOnDescIdDesc(Long userId);
    Optional<Mission> findFirstByUserIdAndStartedOnBetweenOrderByStartedOnDescIdDesc(Long userId, LocalDate from, LocalDate to);

    // CASE는 UPDATE 시점의 doneDays로 판정하여 체크와 경합해도 오래된 값으로 판정하지 않는다.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        update Mission m set m.result = case when m.doneDays >= 5 then :achieved else :failed end,
            m.judgedAt = :now
        where m.userId = :userId and m.result is null and m.startedOn < :earliestActiveStart
        """)
    int judgeExpired(@Param("userId") Long userId, @Param("earliestActiveStart") LocalDate earliestActiveStart,
                     @Param("now") Instant now, @Param("achieved") MissionResult achieved,
                     @Param("failed") MissionResult failed);
}
