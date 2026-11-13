package com.hosunton.backend.domain.expense;

/**
 * 소비 항목. DB에는 이름 그대로 저장한다 (expenses.item CHECK 제약과 일치).
 */
public enum ExpenseItem {
    FOOD,       // 식비
    DELIVERY,   // 배달 음식
    CAFE,       // 카페
    SHOPPING,   // 쇼핑
    TRANSPORT,  // 교통비
    ETC         // 기타
}
