package com.hosunton.backend.domain.home;

import com.hosunton.backend.domain.expense.ExpenseRepository;
import com.hosunton.backend.domain.home.dto.HomeResponse;
import com.hosunton.backend.domain.mission.MissionQueryService;
import com.hosunton.backend.domain.user.User;
import com.hosunton.backend.domain.user.UserRepository;
import com.hosunton.backend.global.exception.BusinessException;
import com.hosunton.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class HomeService {

    private final UserRepository userRepository;
    private final ExpenseRepository expenseRepository;
    private final MissionQueryService missionQueryService;
    private final Clock clock;

    /**
     * readOnly로 두면 안 된다. getActiveMission()이 기간 끝난 미션을 판정하며 UPDATE를 실행하는데,
     * read-only 트랜잭션에서는 PostgreSQL이 UPDATE를 거부한다.
     */
    @Transactional
    public HomeResponse getHome(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        WeekPeriod week = WeekPeriod.of(LocalDate.now(clock));
        LocalDate from = week.startDate();
        LocalDate to = week.endDate();

        long spentAmount = expenseRepository.sumAmount(userId, from, to);
        long expenseCount = expenseRepository.countByUserIdAndSpentOnBetween(userId, from, to);
        long regretCount = expenseRepository.countByUserIdAndSpentOnBetweenAndRegretTrue(userId, from, to);

        return new HomeResponse(
                user.getNickname(),
                HomeResponse.Week.from(week),
                HomeResponse.Budget.of(user.getWeeklyBudget(), spentAmount),
                new HomeResponse.Summary(expenseCount, regretCount),
                HomeResponse.Mission.from(missionQueryService.getActiveMission(userId))
        );
    }
}