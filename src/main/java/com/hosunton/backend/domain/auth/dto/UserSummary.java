package com.hosunton.backend.domain.auth.dto;

import com.hosunton.backend.domain.user.User;

public record UserSummary(Long userId, String nickname, Integer weeklyBudget) {

    public static UserSummary from(User user) {
        return new UserSummary(user.getId(), user.getNickname(), user.getWeeklyBudget());
    }
}