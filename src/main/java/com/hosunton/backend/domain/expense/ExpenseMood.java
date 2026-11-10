package com.hosunton.backend.domain.expense;

/**
 * 소비 당시 기분 (선택 입력). DB에는 이름 그대로 저장한다 (expenses.mood CHECK 제약과 일치).
 */
public enum ExpenseMood {
    GOOD,       // 좋음
    NORMAL,     // 보통
    TIRED,      // 지침
    DEPRESSED   // 우울
}
