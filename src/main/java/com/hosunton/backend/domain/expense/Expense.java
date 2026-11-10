package com.hosunton.backend.domain.expense;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "expenses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "spent_on", nullable = false)
    private LocalDate spentOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExpenseItem item;

    @Column(nullable = false)
    private Integer amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExpenseReason reason;

    @Column(nullable = false)
    private boolean regret;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ExpenseMood mood;

    @Column(length = 100)
    private String memo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static Expense create(Long userId, LocalDate spentOn, ExpenseItem item, int amount,
                                 ExpenseReason reason, boolean regret, ExpenseMood mood, String memo,
                                 Instant now) {
        Expense expense = new Expense();
        expense.userId = userId;
        expense.spentOn = spentOn;
        expense.item = item;
        expense.amount = amount;
        expense.reason = reason;
        expense.regret = regret;
        expense.mood = mood;
        expense.memo = memo;
        expense.createdAt = now;
        return expense;
    }
}
