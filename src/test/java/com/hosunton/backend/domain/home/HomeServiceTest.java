package com.hosunton.backend.domain.home;

import com.hosunton.backend.domain.expense.ExpenseRepository;
import com.hosunton.backend.domain.home.dto.HomeResponse;
import com.hosunton.backend.domain.mission.MissionLevel;
import com.hosunton.backend.domain.mission.MissionQueryService;
import com.hosunton.backend.domain.mission.MissionStatus;
import com.hosunton.backend.domain.mission.PatternGroup;
import com.hosunton.backend.domain.mission.dto.MissionResponse;
import com.hosunton.backend.domain.user.User;
import com.hosunton.backend.domain.user.UserRepository;
import com.hosunton.backend.global.exception.BusinessException;
import com.hosunton.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HomeServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate MON = LocalDate.of(2026, 11, 16);
    private static final LocalDate SUN = LocalDate.of(2026, 11, 22);

    @Mock
    private UserRepository userRepository;

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private MissionQueryService missionQueryService;

    private HomeService homeService;

    @BeforeEach
    void setUp() {
        // 2026-11-18(수) 12:00 KST
        Clock clock = Clock.fixed(Instant.parse("2026-11-18T03:00:00Z"), ZoneId.of("Asia/Seoul"));
        homeService = new HomeService(userRepository, expenseRepository, missionQueryService, clock);
    }

    private User user(Integer weeklyBudget) {
        User user = User.create("harin@example.com", "하린", "hash", Instant.parse("2026-11-01T00:00:00Z"));
        ReflectionTestUtils.setField(user, "id", USER_ID);
        if (weeklyBudget != null) {
            user.updateWeeklyBudget(weeklyBudget);
        }
        return user;
    }

    private void givenWeekExpenses(long spent, long count, long regret) {
        when(expenseRepository.sumAmount(USER_ID, MON, SUN)).thenReturn(spent);
        when(expenseRepository.countByUserIdAndSpentOnBetween(USER_ID, MON, SUN)).thenReturn(count);
        when(expenseRepository.countByUserIdAndSpentOnBetweenAndRegretTrue(USER_ID, MON, SUN)).thenReturn(regret);
    }

    private MissionResponse activeMission(short doneDays, boolean checkedToday) {
        return new MissionResponse(12L, "주문 전 집에 있는 식재료 확인하기", PatternGroup.STRESS_DELIVERY,
                MissionLevel.BASIC, LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 22),
                doneDays, checkedToday, MissionStatus.IN_PROGRESS, false, null, null);
    }

    @Test
    void 미션_진행_중_S3_2() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(100000)));
        givenWeekExpenses(38000, 5, 2);
        when(missionQueryService.getActiveMission(USER_ID)).thenReturn(activeMission((short) 2, false));

        HomeResponse home = homeService.getHome(USER_ID);

        assertThat(home.nickname()).isEqualTo("하린");
        assertThat(home.week().label()).isEqualTo("11월 3주차");
        assertThat(home.week().startDate()).isEqualTo(MON);
        assertThat(home.week().endDate()).isEqualTo(SUN);
        assertThat(home.budget().weeklyBudget()).isEqualTo(100000);
        assertThat(home.budget().spentAmount()).isEqualTo(38000);
        assertThat(home.budget().remainingBudget()).isEqualTo(62000);
        assertThat(home.budget().overBudget()).isFalse();
        assertThat(home.summary().expenseCount()).isEqualTo(5);
        assertThat(home.summary().regretCount()).isEqualTo(2);
        assertThat(home.mission().missionId()).isEqualTo(12L);
        assertThat(home.mission().doneDays()).isEqualTo((short) 2);
        assertThat(home.mission().checkedToday()).isFalse();
    }

    @Test
    void 미션_없음_S3_1() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(100000)));
        givenWeekExpenses(38000, 5, 2);
        when(missionQueryService.getActiveMission(USER_ID)).thenReturn(null);

        assertThat(homeService.getHome(USER_ID).mission()).isNull();
    }

    @Test
    void 예산_초과면_남은_예산_0_S3_3() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(100000)));
        givenWeekExpenses(112000, 7, 3);
        when(missionQueryService.getActiveMission(USER_ID)).thenReturn(null);

        HomeResponse.Budget budget = homeService.getHome(USER_ID).budget();

        assertThat(budget.spentAmount()).isEqualTo(112000);
        assertThat(budget.remainingBudget()).isZero();
        assertThat(budget.overBudget()).isTrue();
    }

    @Test
    void 예산을_정확히_다_쓰면_초과가_아니다() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(100000)));
        givenWeekExpenses(100000, 3, 0);
        when(missionQueryService.getActiveMission(USER_ID)).thenReturn(null);

        HomeResponse.Budget budget = homeService.getHome(USER_ID).budget();

        assertThat(budget.remainingBudget()).isZero();
        assertThat(budget.overBudget()).isFalse();
    }

    @Test
    void 예산_미설정이면_남은_예산_null() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(null)));
        givenWeekExpenses(0, 0, 0);
        when(missionQueryService.getActiveMission(USER_ID)).thenReturn(null);

        HomeResponse.Budget budget = homeService.getHome(USER_ID).budget();

        assertThat(budget.weeklyBudget()).isNull();
        assertThat(budget.spentAmount()).isZero();
        assertThat(budget.remainingBudget()).isNull();
        assertThat(budget.overBudget()).isFalse();
    }

    @Test
    void 탈퇴한_사용자면_UNAUTHORIZED() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> homeService.getHome(USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNAUTHORIZED);
    }
}