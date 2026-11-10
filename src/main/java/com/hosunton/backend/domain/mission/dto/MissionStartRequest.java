package com.hosunton.backend.domain.mission.dto;

import jakarta.validation.constraints.*;
public record MissionStartRequest(@NotNull Long recommendationSetId, @NotNull @Min(0) @Max(2) Integer optionIndex) {}
