package com.hosunton.backend.domain.user;

import com.hosunton.backend.domain.user.dto.MeResponse;
import com.hosunton.backend.domain.user.dto.WeeklyBudgetRequest;
import com.hosunton.backend.domain.user.dto.WeeklyBudgetResponse;
import com.hosunton.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "사용자")
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "내 정보 조회", description = "닉네임, 이메일, 주간 예산, 예산 설정 여부, 누적 소비 기록 수를 조회합니다.")
    @GetMapping
    public ApiResponse<MeResponse> getMe(@Parameter(hidden = true) @AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(userService.getMe(userId));
    }

    @Operation(summary = "주간 예산 설정", description = "주간 예산을 최초 설정하거나 변경합니다. 매주 같은 금액이 이어지며 이력은 저장하지 않습니다.")
    @PutMapping("/weekly-budget")
    public ApiResponse<WeeklyBudgetResponse> updateWeeklyBudget(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @RequestBody WeeklyBudgetRequest request) {
        return ApiResponse.ok(userService.updateWeeklyBudget(userId, request));
    }
}