package com.hosunton.backend.domain.mission;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.*;
import java.util.Optional;

public interface MissionRepository extends JpaRepository<Mission, Long> {
    // 추천이 아직 없는 최초 조회도 사용자별로 직렬화한다. 스키마 변경 없이 중복 생성을 방지한다.
    @Query(value = "select id from users where id = :userId for update", nativeQuery = true)
    Long lockUserForOverview(@Param("userId") Long userId);

    Optional<Mission> findByUserIdAndResultIsNull(Long userId);
    Optional<Mission> findFirstByUserIdAndResultIsNotNullOrderByStartedOnDescIdDesc(Long userId);
    Optional<Mission> findFirstByUserIdAndResultAndDifficultyIsNullOrderByStartedOnDescIdDesc(Long userId, MissionResult result);
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
