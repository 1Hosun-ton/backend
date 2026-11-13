package com.hosunton.backend.domain.mission.dto;

import com.hosunton.backend.domain.mission.MissionDifficulty;
import jakarta.validation.constraints.NotNull;
public record MissionDifficultyRequest(@NotNull MissionDifficulty difficulty) {}
