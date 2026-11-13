package com.hosunton.backend.domain.mission;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.List;
import com.hosunton.backend.domain.mission.dto.RecommendationItem;

@Entity
@Table(name = "mission_recommendations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MissionRecommendation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false)
    private List<RecommendationItem> items;
    @Enumerated(EnumType.STRING) @Column(name = "base_level", nullable = false, length = 10)
    private MissionLevel baseLevel;
    @Column(nullable = false)
    private boolean selected;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static MissionRecommendation create(Long userId, List<RecommendationItem> items,
                                                MissionLevel baseLevel, Instant now) {
        MissionRecommendation r = new MissionRecommendation();
        r.userId = userId; r.items = List.copyOf(items); r.baseLevel = baseLevel; r.createdAt = now;
        return r;
    }
}
