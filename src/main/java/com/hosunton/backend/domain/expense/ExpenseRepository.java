package com.hosunton.backend.domain.expense;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long>, JpaSpecificationExecutor<Expense> {

    Optional<Expense> findByIdAndUserId(Long id, Long userId);

    /** 기간 내 소비 총액. 건당 금액은 integer지만 합계는 범위를 넘을 수 있어 Long으로 받는다. */
    @Query("""
            select coalesce(sum(e.amount), 0L) from Expense e
            where e.userId = :userId and e.spentOn between :from and :to
            """)
    long sumAmount(@Param("userId") Long userId, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
