package com.hosunton.backend.domain.mission;

import com.hosunton.backend.domain.mission.dto.MissionOverviewResponse;
import com.hosunton.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@Tag(name = "미션")
@RestController
@RequestMapping("/api/v1/missions")
@RequiredArgsConstructor
public class MissionController {
    private final MissionService service;
    @Operation(summary = "미션 탭 통합 조회", description = "주간 리포트·추천 3개·미션 상태를 조회하고 종료 미션을 판정합니다. 누적 기록 5건 미만도 200으로 응답합니다.")
    @GetMapping("/overview")
    public ApiResponse<MissionOverviewResponse> getOverview(@Parameter(hidden = true) @AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(service.getOverview(userId));
    }
}
