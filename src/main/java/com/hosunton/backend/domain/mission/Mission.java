package com.hosunton.backend.domain.mission;

import jakarta.persistence.*;
import lombok.*;
import java.time.*;

@Entity
@Table(name = "missions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Mission {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "recommendation_id")
    private Long recommendationId;
    @Enumerated(EnumType.STRING) @Column(name = "pattern_group", nullable = false, length = 20)
    private PatternGroup patternGroup;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10)
    private MissionLevel level;
    @Column(nullable = false, length = 100)
    private String text;
    @Column(name = "started_on", nullable = false)
    private LocalDate startedOn;
    @Column(name = "done_days", nullable = false)
    private Short doneDays;
    @Column(name = "last_checked_on")
    private LocalDate lastCheckedOn;
    @Enumerated(EnumType.STRING) @Column(length = 10)
    private MissionResult result;
    @Enumerated(EnumType.STRING) @Column(length = 20)
    private MissionDifficulty difficulty;
    @Column(length = 100)
    private String memo;
    @Column(name = "judged_at")
    private Instant judgedAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static Mission create(Long userId, Long recommendationId, PatternGroup group,
                                 MissionLevel level, String text, LocalDate startedOn, Instant now) {
        Mission m = new Mission();
        m.userId = userId; m.recommendationId = recommendationId; m.patternGroup = group;
        m.level = level; m.text = text; m.startedOn = startedOn; m.doneDays = 0; m.createdAt = now;
        return m;
    }
}
