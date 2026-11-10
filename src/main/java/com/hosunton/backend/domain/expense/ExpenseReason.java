package com.hosunton.backend.domain.expense;

/**
 * 소비 이유. DB에는 이름 그대로 저장한다 (expenses.reason CHECK 제약과 일치).
 */
public enum ExpenseReason {
    NEED,      // 필요해서
    STRESS,    // 스트레스 해소
    DISCOUNT,  // 할인
    IMPULSE,   // 충동
    REWARD,    // 보상
    HABIT      // 습관
}
