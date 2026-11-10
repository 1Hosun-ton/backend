package com.hosunton.backend.domain.expense;

import com.hosunton.backend.domain.expense.dto.ExpenseCreateRequest;
import com.hosunton.backend.domain.expense.dto.ExpenseListResponse;
import com.hosunton.backend.domain.expense.dto.ExpenseResponse;
import com.hosunton.backend.global.exception.BusinessException;
import com.hosunton.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExpenseService {

    private static final Sort LATEST_FIRST = Sort.by(Sort.Order.desc("spentOn"), Sort.Order.desc("createdAt"));

    private final ExpenseRepository expenseRepository;
    private final Clock clock;

    @Transactional
    public ExpenseResponse create(Long userId, ExpenseCreateRequest request) {
        if (request.amount() == null || request.amount() <= 0) {
            throw new BusinessException(ErrorCode.AMOUNT_REQUIRED);
        }
        if (request.reason() == null || request.regret() == null) {
            throw new BusinessException(ErrorCode.REASON_REGRET_REQUIRED);
        }

        LocalDate today = LocalDate.now(clock);
        LocalDate spentOn = request.spentAt() != null ? request.spentAt() : today;
        if (spentOn.isAfter(today)) {
            throw new BusinessException(ErrorCode.FUTURE_DATE_NOT_ALLOWED);
        }

        Expense expense = Expense.create(
                userId,
                spentOn,
                request.item(),
                request.amount().intValue(), // @Max(Integer.MAX_VALUE)로 범위가 보장됨
                request.reason(),
                request.regret(),
                request.mood(),
                request.memo(),
                clock.instant()
        );
        return ExpenseResponse.from(expenseRepository.save(expense));
    }

    /**
     * month와 from/to 중 하나로 조회한다. 둘 다 없으면 이번 달.
     */
    public ExpenseListResponse list(Long userId, YearMonth month, LocalDate from, LocalDate to,
                                    ExpenseItem item, ExpenseReason reason) {
        boolean rangeQuery = from != null || to != null;
        if (rangeQuery) {
            if (month != null || from == null || to == null || from.isAfter(to)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR);
            }
        }

        LocalDate periodFrom;
        LocalDate periodTo;
        ExpenseListResponse.Summary summary = null;
        if (rangeQuery) {
            periodFrom = from;
            periodTo = to;
        } else {
            YearMonth target = month != null ? month : YearMonth.now(clock);
            periodFrom = target.atDay(1);
            periodTo = target.atEndOfMonth();
            summary = monthlySummary(userId, target, periodFrom, periodTo);
        }

        List<ExpenseResponse> expenses = expenseRepository
                .findAll(ExpenseSpecifications.search(userId, periodFrom, periodTo, item, reason), LATEST_FIRST)
                .stream()
                .map(ExpenseResponse::from)
                .toList();

        return new ExpenseListResponse(
                new ExpenseListResponse.Period(periodFrom, periodTo),
                summary,
                expenses.size(),
                expenses
        );
    }

    @Transactional
    public void delete(Long userId, Long expenseId) {
        Expense expense = expenseRepository.findByIdAndUserId(expenseId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        expenseRepository.delete(expense);
    }

    /**
     * 월 요약 (필터 미적용, 그 달 전체 기준).
     * 하루 평균 = 총액 ÷ 경과 일수 (이번 달은 오늘까지, 지난 달은 그 달 일수), 원 단위 반올림.
     * 주 평균 = 하루 평균 × 7.
     */
    private ExpenseListResponse.Summary monthlySummary(Long userId, YearMonth month, LocalDate from, LocalDate to) {
        long total = expenseRepository.sumAmount(userId, from, to);
        int elapsedDays = elapsedDays(month);
        long dailyAverage = elapsedDays == 0 ? 0 : Math.round((double) total / elapsedDays);
        return new ExpenseListResponse.Summary(total, dailyAverage, dailyAverage * 7);
    }

    private int elapsedDays(YearMonth month) {
        YearMonth current = YearMonth.now(clock);
        if (month.isBefore(current)) {
            return month.lengthOfMonth();
        }
        if (month.equals(current)) {
            return LocalDate.now(clock).getDayOfMonth();
        }
        return 0; // 미래 달: 기록이 없으므로 평균 0
    }
}
