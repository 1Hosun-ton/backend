package com.hosunton.backend.domain.mission;

import com.hosunton.backend.domain.mission.dto.MissionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
@Service
@RequiredArgsConstructor
public class MissionQueryService {
    private final MissionRepository missions;
    private final MissionJudge judge;
    private final Clock clock;
    /** Home용: 종료 판정을 먼저 실행하고 진행 중 미션만 반환한다. */
    @Transactional
    public MissionResponse getActiveMission(Long userId) {
        LocalDate today = LocalDate.now(clock);
        judge.judgeExpired(userId, today, clock.instant());
        return missions.findByUserIdAndResultIsNull(userId).map(m -> MissionResponse.from(m, today)).orElse(null);
    }
}
