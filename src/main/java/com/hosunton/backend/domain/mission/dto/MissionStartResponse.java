package com.hosunton.backend.domain.mission.dto;

import com.hosunton.backend.domain.mission.*;
import java.time.LocalDate;
public record MissionStartResponse(Long missionId, String text, PatternGroup patternGroup, MissionLevel level,
        LocalDate startedOn, LocalDate endsOn, short doneDays, MissionStatus status) {
    public static MissionStartResponse from(Mission m) {
        return new MissionStartResponse(m.getId(), m.getText(), m.getPatternGroup(), m.getLevel(),
                m.getStartedOn(), m.getStartedOn().plusDays(6), m.getDoneDays(), MissionStatus.IN_PROGRESS);
    }
}
