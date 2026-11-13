package com.hosunton.backend.domain.mission;

import com.hosunton.backend.domain.mission.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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

    @Operation(summary = "추천 미션 선택·시작", description = "저장된 추천 중 하나로 KST 오늘부터 7일 미션을 시작합니다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MissionStartResponse> start(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @Valid @RequestBody MissionStartRequest request) {
        return ApiResponse.ok(service.start(userId, request));
    }

    @Operation(summary = "오늘 실천 체크", description = "하루 1회, 시작일+6일까지 체크합니다. 메모는 선택이며 최근 1개만 보관합니다.")
    @PostMapping("/{missionId}/checks")
    public ApiResponse<MissionCheckResponse> check(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @PathVariable Long missionId, @Valid @RequestBody(required = false) MissionCheckRequest request) {
        return ApiResponse.ok(service.check(userId, missionId, request));
    }

    @Operation(summary = "미달성 사유 저장", description = "FAILED 미션에만 저장할 수 있으며 재입력 시 덮어씁니다.")
    @PatchMapping("/{missionId}/difficulty")
    public ApiResponse<MissionDifficultyResponse> updateDifficulty(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @PathVariable Long missionId, @Valid @RequestBody MissionDifficultyRequest request) {
        return ApiResponse.ok(service.updateDifficulty(userId, missionId, request));
    }
}
