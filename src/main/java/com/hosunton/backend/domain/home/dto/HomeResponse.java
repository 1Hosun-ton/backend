package com.hosunton.backend.domain.home.dto;

import com.hosunton.backend.domain.home.WeekPeriod;
import com.hosunton.backend.domain.mission.dto.MissionResponse;

import java.time.LocalDate;

public record HomeResponse(
        String nickname,
        Week week,
        Budget budget,
        Summary summary,
        Mission mission
) {

    public record Week(String label, LocalDate startDate, LocalDate endDate) {
        public static Week from(WeekPeriod period) {
            return new Week(period.label(), period.startDate(), period.endDate());
        }
    }

    public record Budget(Integer weeklyBudget, long spentAmount, Long remainingBudget, boolean overBudget) {
        public static Budget of(Integer weeklyBudget, long spentAmount) {
            if (weeklyBudget == null) {
                return new Budget(null, spentAmount, null, false);
            }
            long remaining = Math.max(weeklyBudget - spentAmount, 0);
            return new Budget(weeklyBudget, spentAmount, remaining, spentAmount > weeklyBudget);
        }
    }

    public record Summary(long expenseCount, long regretCount) {
    }

    public record Mission(Long missionId, String text, short doneDays, boolean checkedToday) {
        public static Mission from(MissionResponse response) {
            if (response == null) {
                return null;
            }
            return new Mission(response.missionId(), response.text(), response.doneDays(), response.checkedToday());
        }
    }
}