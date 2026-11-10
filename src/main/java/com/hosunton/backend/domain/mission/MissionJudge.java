package com.hosunton.backend.domain.mission;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
@Service
@RequiredArgsConstructor
public class MissionJudge {
    private final MissionRepository missions;
    @Transactional
    public void judgeExpired(Long userId, LocalDate today, Instant now) {
        missions.judgeExpired(userId, today.minusDays(6), now, MissionResult.ACHIEVED, MissionResult.FAILED);
    }
}
