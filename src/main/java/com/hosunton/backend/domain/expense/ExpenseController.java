package com.hosunton.backend.domain.expense;

import com.hosunton.backend.domain.expense.dto.ExpenseCreateRequest;
import com.hosunton.backend.domain.expense.dto.ExpenseListResponse;
import com.hosunton.backend.domain.expense.dto.ExpenseResponse;
import com.hosunton.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;

@Tag(name = "소비 기록")
@RestController
@RequestMapping("/api/v1/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;

    @Operation(summary = "소비 기록 등록", description = "날짜를 생략하면 오늘(KST)로 저장합니다. 미래 날짜는 저장할 수 없습니다.")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ApiResponse<ExpenseResponse> create(@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
                                               @Valid @RequestBody ExpenseCreateRequest request) {
        return ApiResponse.ok(expenseService.create(userId, request));
    }

    @Operation(summary = "소비 기록 목록 조회",
            description = "month(yyyy-MM) 또는 from/to(yyyy-MM-dd) 중 하나로 조회합니다. 둘 다 없으면 이번 달입니다. "
                    + "summary는 월 조회일 때만 그 달 전체 기준으로 내려가고, 기간 조회면 null입니다.")
    @GetMapping
    public ApiResponse<ExpenseListResponse> list(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) ExpenseItem item,
            @RequestParam(required = false) ExpenseReason reason) {
        return ApiResponse.ok(expenseService.list(userId, month, from, to, item, reason));
    }

    @Operation(summary = "소비 기록 삭제", description = "본인 기록만 삭제할 수 있습니다. 없거나 다른 사용자의 기록이면 404입니다.")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{expenseId}")
    public void delete(@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
                       @PathVariable Long expenseId) {
        expenseService.delete(userId, expenseId);
    }
}
