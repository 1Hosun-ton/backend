package com.hosunton.backend.domain.user;

import com.hosunton.backend.domain.expense.ExpenseRepository;
import com.hosunton.backend.domain.user.dto.MeResponse;
import com.hosunton.backend.domain.user.dto.WeeklyBudgetRequest;
import com.hosunton.backend.domain.user.dto.WeeklyBudgetResponse;
import com.hosunton.backend.global.exception.BusinessException;
import com.hosunton.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final ExpenseRepository expenseRepository;

    public MeResponse getMe(Long userId) {
        User user = getUser(userId);
        return MeResponse.of(user, expenseRepository.countByUserId(userId));
    }

    @Transactional
    public WeeklyBudgetResponse updateWeeklyBudget(Long userId, WeeklyBudgetRequest request) {
        Integer budget = request.weeklyBudget();
        if (budget == null || budget < 1) {
            throw new BusinessException(ErrorCode.INVALID_BUDGET);
        }

        User user = getUser(userId);
        user.updateWeeklyBudget(budget);
        return new WeeklyBudgetResponse(user.getWeeklyBudget());
    }

    @Transactional
    public void withdraw(Long userId) {
        userRepository.delete(getUser(userId));
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }
}