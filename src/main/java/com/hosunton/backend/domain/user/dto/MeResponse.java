package com.hosunton.backend.domain.user.dto;

import com.hosunton.backend.domain.user.User;

public record MeResponse (
    Long userId,
    String nickname,
    String email,
    Integer weeklyBudget,
    boolean budgetSet,
    long totalExpenseCount
){
    public static MeResponse of(User user, long totalExpenseCount){
        return new MeResponse(
                user.getId(),
                user.getNickname(),
                user.getEmail(),
                user.getWeeklyBudget(),
                user.getWeeklyBudget()!=null,
                totalExpenseCount
        );
    }
}
