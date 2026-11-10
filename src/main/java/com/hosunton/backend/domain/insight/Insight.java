package com.hosunton.backend.domain.insight;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 기간별 인사이트 결과 캐시. AI로 정상 생성한 결과만 저장한다 (기획안 7-5).
 */
@Entity
@Table(name = "insights")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Insight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", nullable = false, length = 10)
    private PeriodType periodType;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    /** InsightResponse를 직렬화한 JSON */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String result;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static Insight create(Long userId, PeriodType periodType, LocalDate periodStart, String result, Instant now) {
        Insight insight = new Insight();
        insight.userId = userId;
        insight.periodType = periodType;
        insight.periodStart = periodStart;
        insight.result = result;
        insight.createdAt = now;
        return insight;
    }
}
