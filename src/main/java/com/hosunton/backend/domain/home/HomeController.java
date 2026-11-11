package com.hosunton.backend.domain.home;

import com.hosunton.backend.domain.home.dto.HomeResponse;
import com.hosunton.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "홈")
@RestController
@RequestMapping("/api/v1/home")
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;

    @Operation(summary = "홈 조회",
            description = "현재 주차(KST 월~일)의 예산 현황, 소비 요약, 진행 중 미션을 조회합니다. "
                    + "예산 초과 시 remainingBudget은 0, overBudget은 true입니다. 진행 중 미션이 없으면 mission은 null입니다.")
    @GetMapping
    public ApiResponse<HomeResponse> getHome(@Parameter(hidden = true) @AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(homeService.getHome(userId));
    }
}