package com.hosunton.backend.domain.expense;

import java.time.LocalDate;

/**
 * 소비 기록이 저장·삭제되었음을 알린다. 트랜잭션 커밋 후 인사이트를 미리 만드는 데 쓴다.
 *
 * @param spentOn 저장·삭제된 기록의 날짜
 */
public record ExpenseChangedEvent(Long userId, LocalDate spentOn) {
}
