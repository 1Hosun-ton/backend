package com.hosunton.backend.domain.mission.dto;

import java.util.List;
public record MissionOverviewResponse(long cumulativeExpenseCount, int requiredExpenseCount,
        WeeklyReportResponse report, Long recommendationSetId, List<RecommendationItem> recommendations,
        MissionResponse mission) {}
