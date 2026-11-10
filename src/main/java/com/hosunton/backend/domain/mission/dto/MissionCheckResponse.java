package com.hosunton.backend.domain.mission.dto;

import com.hosunton.backend.domain.mission.*;
import java.time.LocalDate;
public record MissionCheckResponse(Long missionId, LocalDate checkedOn, short doneDays, boolean checkedToday,
        String memo, MissionStatus status) {
    public static MissionCheckResponse from(Mission m) {
        return new MissionCheckResponse(m.getId(), m.getLastCheckedOn(), m.getDoneDays(), true,
                m.getMemo(), MissionStatus.IN_PROGRESS);
    }
}
