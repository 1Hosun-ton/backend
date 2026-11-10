package com.hosunton.backend.domain.insight;

import com.hosunton.backend.domain.insight.dto.InsightResponse;
import com.hosunton.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "인사이트")
@RestController
@RequestMapping("/api/v1/insights")
@RequiredArgsConstructor
public class InsightController {

    private final InsightService insightService;

    @Operation(summary = "AI 소비 인사이트 조회",
            description = "periodType 기본값은 MONTH, periodStart 기본값은 현재 기간입니다. "
                    + "주간은 월요일, 월간은 1일이어야 하며 미래 기간은 조회할 수 없습니다. "
                    + "status: INSUFFICIENT_DATA(누적 5건 미만) / NO_EXPENSES(기간 내 기록 없음) / NO_PATTERN / READY")
    @GetMapping
    public ApiResponse<InsightResponse> getInsight(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) PeriodType periodType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart) {
        return ApiResponse.ok(insightService.getInsight(userId, periodType, periodStart));
    }
}
