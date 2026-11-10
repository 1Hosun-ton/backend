package com.hosunton.backend.domain.mission;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.Optional;
public interface MissionRecommendationRepository extends JpaRepository<MissionRecommendation, Long> {
    Optional<MissionRecommendation> findFirstByUserIdAndSelectedFalseAndCreatedAtAfterOrderByCreatedAtDescIdDesc(Long userId, Instant after);
}
