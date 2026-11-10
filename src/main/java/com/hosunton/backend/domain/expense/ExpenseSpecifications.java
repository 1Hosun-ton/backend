package com.hosunton.backend.domain.expense;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 소비 기록 목록 조회 조건. item·reason은 값이 있을 때만 조건에 넣는다.
 */
final class ExpenseSpecifications {

    private ExpenseSpecifications() {
    }

    static Specification<Expense> search(Long userId, LocalDate from, LocalDate to,
                                         ExpenseItem item, ExpenseReason reason) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("userId"), userId));
            predicates.add(cb.between(root.get("spentOn"), from, to));
            if (item != null) {
                predicates.add(cb.equal(root.get("item"), item));
            }
            if (reason != null) {
                predicates.add(cb.equal(root.get("reason"), reason));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
