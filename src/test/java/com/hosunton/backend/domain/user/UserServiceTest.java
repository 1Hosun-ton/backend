package com.hosunton.backend.domain.user;

import com.hosunton.backend.domain.expense.ExpenseRepository;
import com.hosunton.backend.domain.user.dto.MeResponse;
import com.hosunton.backend.domain.user.dto.WeeklyBudgetRequest;
import com.hosunton.backend.domain.user.dto.WeeklyBudgetResponse;
import com.hosunton.backend.global.exception.BusinessException;
import com.hosunton.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ExpenseRepository expenseRepository;

    @InjectMocks
    private UserService userService;

    private User user(Integer weeklyBudget) {
        User user = User.create("harin@example.com", "하린", "hash", Instant.parse("2026-11-09T03:00:00Z"));
        ReflectionTestUtils.setField(user, "id", USER_ID);
        if (weeklyBudget != null) {
            user.updateWeeklyBudget(weeklyBudget);
        }
        return user;
    }

    @Test
    void 내_정보를_조회한다() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(100000)));
        when(expenseRepository.countByUserId(USER_ID)).thenReturn(12L);

        MeResponse response = userService.getMe(USER_ID);

        assertThat(response.userId()).isEqualTo(USER_ID);
        assertThat(response.nickname()).isEqualTo("하린");
        assertThat(response.email()).isEqualTo("harin@example.com");
        assertThat(response.weeklyBudget()).isEqualTo(100000);
        assertThat(response.budgetSet()).isTrue();
        assertThat(response.totalExpenseCount()).isEqualTo(12L);
    }

    @Test
    void 예산을_설정하지_않았으면_budgetSet은_false() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(null)));
        when(expenseRepository.countByUserId(USER_ID)).thenReturn(0L);

        MeResponse response = userService.getMe(USER_ID);

        assertThat(response.weeklyBudget()).isNull();
        assertThat(response.budgetSet()).isFalse();
    }

    @Test
    void 탈퇴한_사용자면_UNAUTHORIZED() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getMe(USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void 주간_예산을_설정한다() {
        User user = user(null);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        WeeklyBudgetResponse response = userService.updateWeeklyBudget(USER_ID, new WeeklyBudgetRequest(100000));

        assertThat(response.weeklyBudget()).isEqualTo(100000);
        assertThat(user.getWeeklyBudget()).isEqualTo(100000);
    }

    @Test
    void 주간_예산을_변경한다() {
        User user = user(100000);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        userService.updateWeeklyBudget(USER_ID, new WeeklyBudgetRequest(70000));

        assertThat(user.getWeeklyBudget()).isEqualTo(70000);
    }

    @Test
    void 예산이_없으면_INVALID_BUDGET() {
        assertThatThrownBy(() -> userService.updateWeeklyBudget(USER_ID, new WeeklyBudgetRequest(null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_BUDGET);
        verify(userRepository, never()).findById(anyLong());
    }

    @Test
    void 예산이_0원이면_INVALID_BUDGET() {
        assertThatThrownBy(() -> userService.updateWeeklyBudget(USER_ID, new WeeklyBudgetRequest(0)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_BUDGET);
    }

    @Test
    void 예산_1원은_허용한다() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(null)));

        WeeklyBudgetResponse response = userService.updateWeeklyBudget(USER_ID, new WeeklyBudgetRequest(1));

        assertThat(response.weeklyBudget()).isEqualTo(1);
    }

    @Test
    void 탈퇴하면_사용자를_삭제한다() {
        User user = user(100000);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        userService.withdraw(USER_ID);

        verify(userRepository).delete(user);
    }

    @Test
    void 이미_탈퇴한_사용자면_UNAUTHORIZED() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.withdraw(USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNAUTHORIZED);
        verify(userRepository, never()).delete(any(User.class));
    }
}