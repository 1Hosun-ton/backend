package com.hosunton.backend.domain.expense;

import com.hosunton.backend.domain.expense.dto.ExpenseCreateRequest;
import com.hosunton.backend.domain.expense.dto.ExpenseListResponse;
import com.hosunton.backend.domain.expense.dto.ExpenseResponse;
import com.hosunton.backend.global.exception.BusinessException;
import com.hosunton.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    /** 2026-11-09 (월) 12:00 KST */
    private static final Instant NOW = Instant.parse("2026-11-09T03:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 9);
    private static final Long USER_ID = 1L;

    @Mock
    private ExpenseRepository expenseRepository;

    private ExpenseService expenseService;

    @BeforeEach
    void setUp() {
        expenseService = new ExpenseService(expenseRepository, Clock.fixed(NOW, KST));
    }

    private ExpenseCreateRequest request(Long amount, LocalDate spentAt, ExpenseReason reason, Boolean regret) {
        return new ExpenseCreateRequest(amount, ExpenseItem.DELIVERY, spentAt, reason, regret, ExpenseMood.TIRED, "야근 후 배달");
    }

    private void givenSaveAssignsId(long id) {
        when(expenseRepository.save(any(Expense.class))).thenAnswer(invocation -> {
            Expense expense = invocation.getArgument(0);
            ReflectionTestUtils.setField(expense, "id", id);
            return expense;
        });
    }

    // ===== 등록 =====

    @Test
    void 소비_기록을_등록하면_저장된_내용을_반환한다() {
        givenSaveAssignsId(10L);

        ExpenseResponse response = expenseService.create(USER_ID,
                request(25000L, LocalDate.of(2026, 11, 7), ExpenseReason.STRESS, true));

        assertThat(response.expenseId()).isEqualTo(10L);
        assertThat(response.amount()).isEqualTo(25000L);
        assertThat(response.item()).isEqualTo(ExpenseItem.DELIVERY);
        assertThat(response.spentAt()).isEqualTo(LocalDate.of(2026, 11, 7));
        assertThat(response.reason()).isEqualTo(ExpenseReason.STRESS);
        assertThat(response.regret()).isTrue();
        assertThat(response.mood()).isEqualTo(ExpenseMood.TIRED);
        assertThat(response.memo()).isEqualTo("야근 후 배달");
        assertThat(response.createdAt().toInstant()).isEqualTo(NOW);
        assertThat(response.createdAt().getOffset().getTotalSeconds()).isEqualTo(9 * 3600);

        ArgumentCaptor<Expense> saved = ArgumentCaptor.forClass(Expense.class);
        verify(expenseRepository).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(USER_ID);
    }

    @Test
    void 날짜를_생략하면_오늘로_저장한다() {
        givenSaveAssignsId(1L);

        ExpenseResponse response = expenseService.create(USER_ID, request(1000L, null, ExpenseReason.NEED, false));

        assertThat(response.spentAt()).isEqualTo(TODAY);
    }

    @Test
    void 오늘_날짜는_저장할_수_있다() {
        givenSaveAssignsId(1L);

        ExpenseResponse response = expenseService.create(USER_ID, request(1000L, TODAY, ExpenseReason.NEED, false));

        assertThat(response.spentAt()).isEqualTo(TODAY);
    }

    @Test
    void 미래_날짜는_FUTURE_DATE_NOT_ALLOWED() {
        assertThatThrownBy(() -> expenseService.create(USER_ID,
                request(1000L, TODAY.plusDays(1), ExpenseReason.NEED, false)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FUTURE_DATE_NOT_ALLOWED);
        verify(expenseRepository, never()).save(any());
    }

    @Test
    void 금액이_없거나_0_이하면_AMOUNT_REQUIRED() {
        for (Long amount : new Long[]{null, 0L, -1000L}) {
            assertThatThrownBy(() -> expenseService.create(USER_ID, request(amount, null, ExpenseReason.NEED, false)))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.AMOUNT_REQUIRED);
        }
        verify(expenseRepository, never()).save(any());
    }

    @Test
    void 이유나_후회_여부가_없으면_REASON_REGRET_REQUIRED() {
        assertThatThrownBy(() -> expenseService.create(USER_ID, request(1000L, null, null, true)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.REASON_REGRET_REQUIRED);
        assertThatThrownBy(() -> expenseService.create(USER_ID, request(1000L, null, ExpenseReason.NEED, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.REASON_REGRET_REQUIRED);
        verify(expenseRepository, never()).save(any());
    }

    @Test
    void 금액과_이유가_모두_없으면_금액_안내가_먼저다() {
        assertThatThrownBy(() -> expenseService.create(USER_ID, request(null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AMOUNT_REQUIRED);
    }

    // ===== 목록 =====

    @SuppressWarnings("unchecked")
    private void givenNoExpenses() {
        when(expenseRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());
    }

    @Test
    void 조건이_없으면_이번_달을_조회하고_오늘까지_경과_일수로_평균을_낸다() {
        givenNoExpenses();
        when(expenseRepository.sumAmount(USER_ID, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)))
                .thenReturn(63000L);

        ExpenseListResponse response = expenseService.list(USER_ID, null, null, null, null, null);

        assertThat(response.period().from()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(response.period().to()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(response.summary().totalAmount()).isEqualTo(63000L);
        assertThat(response.summary().dailyAverage()).isEqualTo(7000L);   // 63000 / 9일
        assertThat(response.summary().weeklyAverage()).isEqualTo(49000L);  // 7000 × 7
        assertThat(response.totalCount()).isZero();
    }

    @Test
    void 지난_달은_그_달_일수로_평균을_내고_원_단위로_반올림한다() {
        givenNoExpenses();
        when(expenseRepository.sumAmount(USER_ID, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)))
                .thenReturn(100000L);

        ExpenseListResponse response = expenseService.list(USER_ID, YearMonth.of(2026, 10), null, null, null, null);

        assertThat(response.summary().dailyAverage()).isEqualTo(3226L);   // 100000 / 31 = 3225.8
        assertThat(response.summary().weeklyAverage()).isEqualTo(22582L);  // 3226 × 7
    }

    @Test
    void 미래_달은_평균이_0이다() {
        givenNoExpenses();
        when(expenseRepository.sumAmount(eq(USER_ID), any(), any())).thenReturn(0L);

        ExpenseListResponse response = expenseService.list(USER_ID, YearMonth.of(2026, 12), null, null, null, null);

        assertThat(response.summary().dailyAverage()).isZero();
        assertThat(response.summary().weeklyAverage()).isZero();
    }

    @Test
    void 기간_조회는_summary가_null이다() {
        givenNoExpenses();

        ExpenseListResponse response = expenseService.list(USER_ID, null,
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 7), ExpenseItem.DELIVERY, ExpenseReason.STRESS);

        assertThat(response.period().from()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(response.period().to()).isEqualTo(LocalDate.of(2026, 11, 7));
        assertThat(response.summary()).isNull();
        verify(expenseRepository, never()).sumAmount(any(), any(), any());
    }

    @Test
    void 조회_조건이_잘못되면_VALIDATION_ERROR() {
        LocalDate d1 = LocalDate.of(2026, 11, 1);
        LocalDate d7 = LocalDate.of(2026, 11, 7);

        // month와 from/to 동시 사용
        assertThatThrownBy(() -> expenseService.list(USER_ID, YearMonth.of(2026, 11), d1, d7, null, null))
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);
        // from만 있음
        assertThatThrownBy(() -> expenseService.list(USER_ID, null, d1, null, null, null))
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);
        // to만 있음
        assertThatThrownBy(() -> expenseService.list(USER_ID, null, null, d7, null, null))
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);
        // from > to
        assertThatThrownBy(() -> expenseService.list(USER_ID, null, d7, d1, null, null))
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void 같은_날은_하루짜리_기간으로_조회할_수_있다() {
        givenNoExpenses();
        LocalDate day = LocalDate.of(2026, 11, 3);

        ExpenseListResponse response = expenseService.list(USER_ID, null, day, day, null, null);

        assertThat(response.period().from()).isEqualTo(day);
        assertThat(response.period().to()).isEqualTo(day);
    }

    // ===== 삭제 =====

    @Test
    void 본인_기록을_삭제한다() {
        Expense expense = Expense.create(USER_ID, TODAY, ExpenseItem.CAFE, 4500, ExpenseReason.HABIT, false, null, null, NOW);
        when(expenseRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(expense));

        expenseService.delete(USER_ID, 5L);

        verify(expenseRepository).delete(expense);
    }

    @Test
    void 없거나_다른_사용자의_기록이면_NOT_FOUND() {
        when(expenseRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> expenseService.delete(USER_ID, 5L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
        verify(expenseRepository, never()).delete(any(Expense.class));
    }
}
