package com.hosunton.backend.domain.mission.dto;

import jakarta.validation.constraints.Size;
public record MissionCheckRequest(@Size(max = 100) String memo) {}
