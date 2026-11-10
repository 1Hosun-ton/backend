package com.hosunton.backend.domain.expense;

import com.hosunton.backend.domain.user.User;
import com.hosunton.backend.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 PostgreSQL(로컬 Docker) + Flyway V1 스키마로 매핑과 쿼리를 검증한다.
 * 각 테스트는 트랜잭션 롤백으로 데이터를 남기지 않는다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ExpenseRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-11-09T03:00:00Z");
    private static final Sort LATEST_FIRST = Sort.by(Sort.Order.desc("spentOn"), Sort.Order.desc("createdAt"));

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private UserRepository userRepository;

    private Long me;
    private Long other;

    @BeforeEach
    void setUp() {
        me = userRepository.save(User.create("me-" + System.nanoTime() + "@test.com", "나", "hash", NOW)).getId();
        other = userRepository.save(User.create("other-" + System.nanoTime() + "@test.com", "남", "hash", NOW)).getId();
    }

    private Expense save(Long userId, LocalDate date, ExpenseItem item, int amount, ExpenseReason reason, Instant createdAt) {
        return expenseRepository.save(Expense.create(userId, date, item, amount, reason, false, ExpenseMood.GOOD, "메모", createdAt));
    }

    @Test
    void 기간과_필터로_본인_기록만_최신순으로_조회한다() {
        LocalDate d3 = LocalDate.of(2026, 11, 3);
        LocalDate d5 = LocalDate.of(2026, 11, 5);
        Expense older = save(me, d3, ExpenseItem.DELIVERY, 20000, ExpenseReason.STRESS, NOW);
        Expense sameDayEarly = save(me, d5, ExpenseItem.DELIVERY, 15000, ExpenseReason.STRESS, NOW);
        Expense sameDayLate = save(me, d5, ExpenseItem.DELIVERY, 18000, ExpenseReason.STRESS, NOW.plusSeconds(60));
        save(me, d5, ExpenseItem.CAFE, 4500, ExpenseReason.STRESS, NOW);                             // 항목 다름
        save(me, d5, ExpenseItem.DELIVERY, 9000, ExpenseReason.NEED, NOW);                           // 이유 다름
        save(me, LocalDate.of(2026, 10, 31), ExpenseItem.DELIVERY, 9000, ExpenseReason.STRESS, NOW); // 기간 밖
        save(other, d5, ExpenseItem.DELIVERY, 30000, ExpenseReason.STRESS, NOW);                     // 다른 사용자

        List<Expense> result = expenseRepository.findAll(
                ExpenseSpecifications.search(me, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
                        ExpenseItem.DELIVERY, ExpenseReason.STRESS),
                LATEST_FIRST);

        assertThat(result).extracting(Expense::getId)
                .containsExactly(sameDayLate.getId(), sameDayEarly.getId(), older.getId());
    }

    @Test
    void 필터가_없으면_기간_내_본인_기록_전체를_조회한다() {
        LocalDate d5 = LocalDate.of(2026, 11, 5);
        save(me, d5, ExpenseItem.DELIVERY, 15000, ExpenseReason.STRESS, NOW);
        save(me, d5, ExpenseItem.CAFE, 4500, ExpenseReason.HABIT, NOW);
        save(other, d5, ExpenseItem.CAFE, 4500, ExpenseReason.HABIT, NOW);

        List<Expense> result = expenseRepository.findAll(
                ExpenseSpecifications.search(me, d5, d5, null, null), LATEST_FIRST);

        assertThat(result).hasSize(2).allMatch(e -> e.getUserId().equals(me));
    }

    @Test
    void 기간_총액은_본인_기록만_Long으로_합산한다() {
        save(me, LocalDate.of(2026, 11, 1), ExpenseItem.FOOD, Integer.MAX_VALUE, ExpenseReason.NEED, NOW);
        save(me, LocalDate.of(2026, 11, 30), ExpenseItem.FOOD, 1, ExpenseReason.NEED, NOW);
        save(me, LocalDate.of(2026, 12, 1), ExpenseItem.FOOD, 500, ExpenseReason.NEED, NOW);    // 기간 밖
        save(other, LocalDate.of(2026, 11, 5), ExpenseItem.FOOD, 700, ExpenseReason.NEED, NOW); // 다른 사용자

        long total = expenseRepository.sumAmount(me, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));

        assertThat(total).isEqualTo((long) Integer.MAX_VALUE + 1);
    }

    @Test
    void 기록이_없으면_총액은_0이다() {
        assertThat(expenseRepository.sumAmount(me, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))).isZero();
    }

    @Test
    void 다른_사용자의_기록은_ID로도_찾을_수_없다() {
        Expense mine = save(me, LocalDate.of(2026, 11, 5), ExpenseItem.FOOD, 1000, ExpenseReason.NEED, NOW);

        assertThat(expenseRepository.findByIdAndUserId(mine.getId(), me)).isPresent();
        assertThat(expenseRepository.findByIdAndUserId(mine.getId(), other)).isEmpty();
    }

    @Test
    void 선택_입력이_없어도_저장된다() {
        Expense saved = expenseRepository.saveAndFlush(Expense.create(me, LocalDate.of(2026, 11, 5),
                ExpenseItem.ETC, 1000, ExpenseReason.REWARD, true, null, null, NOW));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getMood()).isNull();
        assertThat(saved.getMemo()).isNull();
    }
}
