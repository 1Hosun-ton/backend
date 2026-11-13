package com.hosunton.backend.domain.expense.dto;

import com.hosunton.backend.domain.expense.Expense;
import com.hosunton.backend.domain.expense.ExpenseItem;
import com.hosunton.backend.domain.expense.ExpenseMood;
import com.hosunton.backend.domain.expense.ExpenseReason;
import com.hosunton.backend.global.config.ClockConfig;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record ExpenseResponse(
        Long expenseId,
        Long amount,
        ExpenseItem item,
        LocalDate spentAt,
        ExpenseReason reason,
        boolean regret,
        ExpenseMood mood,
        String memo,
        OffsetDateTime createdAt
) {

    public static ExpenseResponse from(Expense expense) {
        return new ExpenseResponse(
                expense.getId(),
                expense.getAmount().longValue(),
                expense.getItem(),
                expense.getSpentOn(),
                expense.getReason(),
                expense.isRegret(),
                expense.getMood(),
                expense.getMemo(),
                OffsetDateTime.ofInstant(expense.getCreatedAt(), ClockConfig.KST)
        );
    }
}
