package com.hosunton.backend.domain.mission.dto;

import com.hosunton.backend.domain.mission.*;
import java.time.LocalDate;
public record MissionResponse(Long missionId, String text, PatternGroup patternGroup, MissionLevel level,
        LocalDate startedOn, LocalDate endsOn, short doneDays, boolean checkedToday, MissionStatus status,
        boolean feedbackRequired, MissionDifficulty difficulty, String memo) {
    public static MissionResponse from(Mission m, LocalDate today) {
        return new MissionResponse(m.getId(), m.getText(), m.getPatternGroup(), m.getLevel(),
            m.getStartedOn(), m.getStartedOn().plusDays(6), m.getDoneDays(), today.equals(m.getLastCheckedOn()),
            m.getResult() == null ? MissionStatus.IN_PROGRESS : MissionStatus.valueOf(m.getResult().name()),
            m.getResult() == MissionResult.FAILED && m.getDifficulty() == null, m.getDifficulty(), m.getMemo());
    }
}
