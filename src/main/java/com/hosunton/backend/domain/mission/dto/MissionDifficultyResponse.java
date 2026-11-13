package com.hosunton.backend.domain.mission.dto;

import com.hosunton.backend.domain.mission.*;
public record MissionDifficultyResponse(Long missionId, MissionStatus status, short doneDays,
        MissionDifficulty difficulty, boolean feedbackRequired) {
    public static MissionDifficultyResponse from(Mission m) {
        return new MissionDifficultyResponse(m.getId(), MissionStatus.FAILED, m.getDoneDays(), m.getDifficulty(), false);
    }
}
