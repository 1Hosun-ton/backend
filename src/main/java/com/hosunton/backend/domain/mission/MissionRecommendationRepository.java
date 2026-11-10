package com.hosunton.backend.domain.mission;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;
public interface MissionRecommendationRepository extends JpaRepository<MissionRecommendation, Long> {
    Optional<MissionRecommendation> findByIdAndUserId(Long id, Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        update MissionRecommendation r set r.selected = true
        where r.id = :id and r.userId = :userId and r.selected = false and r.createdAt > :after
        """)
    int selectOnce(@Param("id") Long id, @Param("userId") Long userId, @Param("after") Instant after);

    Optional<MissionRecommendation> findFirstByUserIdAndSelectedFalseAndCreatedAtAfterOrderByCreatedAtDescIdDesc(Long userId, Instant after);
}
