package com.hosunton.backend.domain.expense.dto;

import com.hosunton.backend.domain.expense.ExpenseItem;
import com.hosunton.backend.domain.expense.ExpenseMood;
import com.hosunton.backend.domain.expense.ExpenseReason;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 소비 기록 등록 요청.
 * amount, reason, regret은 전용 에러 코드(AMOUNT_REQUIRED, REASON_REGRET_REQUIRED)가 있어
 * 공통 규칙에 따라 서비스에서 검증한다. 여기서는 전용 코드가 없는 형식 검증만 한다.
 */
public record ExpenseCreateRequest(
        @Max(value = Integer.MAX_VALUE, message = "금액이 너무 커요")
        Long amount,

        @NotNull(message = "항목을 선택해 주세요")
        ExpenseItem item,

        /** 생략하면 오늘(KST). */
        LocalDate spentAt,

        ExpenseReason reason,

        Boolean regret,

        ExpenseMood mood,

        @Size(max = 100, message = "메모는 100자까지 입력할 수 있어요")
        String memo
) {
}
