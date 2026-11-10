package com.hosunton.backend.domain.mission.dto;

import com.hosunton.backend.domain.mission.*;
public record RecommendationItem(int optionIndex, String text, PatternGroup patternGroup, MissionLevel level) {}
