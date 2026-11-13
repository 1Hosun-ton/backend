package com.hosunton.backend.domain.expense;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long>, JpaSpecificationExecutor<Expense> {

    Optional<Expense> findByIdAndUserId(Long id, Long userId);

    /** 누적 기록 수 (인사이트·미션 5건 기준, 내 정보 조회에서도 사용) */
    long countByUserId(Long userId);

    List<Expense> findByUserIdAndSpentOnBetween(Long userId, LocalDate from, LocalDate to);

    /** 기간 내 소비 총액. 건당 금액은 integer지만 합계는 범위를 넘을 수 있어 Long으로 받는다. */
    @Query("""
            select coalesce(sum(e.amount), 0L) from Expense e
            where e.userId = :userId and e.spentOn between :from and :to
            """)
    long sumAmount(@Param("userId") Long userId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** 기간 내 기록 건수 (홈) */
    long countByUserIdAndSpentOnBetween(Long userId, LocalDate from, LocalDate to);

    /** 기간 내 후회한 소비 건수 (홈) */
    long countByUserIdAndSpentOnBetweenAndRegretTrue(Long userId, LocalDate from, LocalDate to);
}
