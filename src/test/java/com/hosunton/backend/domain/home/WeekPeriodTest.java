package com.hosunton.backend.domain.home;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class WeekPeriodTest {

    @Test
    void 한_달_안의_주() {
        WeekPeriod week = WeekPeriod.of(LocalDate.of(2026, 11, 18)); // 수요일

        assertThat(week.startDate()).isEqualTo(LocalDate.of(2026, 11, 16));
        assertThat(week.endDate()).isEqualTo(LocalDate.of(2026, 11, 22));
        assertThat(week.label()).isEqualTo("11월 3주차");
    }

    @Test
    void 월요일과_일요일은_같은_주() {
        assertThat(WeekPeriod.of(LocalDate.of(2026, 11, 16)).label()).isEqualTo("11월 3주차"); // 월
        assertThat(WeekPeriod.of(LocalDate.of(2026, 11, 22)).label()).isEqualTo("11월 3주차"); // 일
    }

    @Test
    void 월이_바뀌는_주는_목요일이_속한_달() {
        WeekPeriod week = WeekPeriod.of(LocalDate.of(2026, 11, 30)); // 월, 목요일은 12월 3일

        assertThat(week.endDate()).isEqualTo(LocalDate.of(2026, 12, 6));
        assertThat(week.label()).isEqualTo("12월 1주차");
    }

    @Test
    void 목요일이_전달이면_전달_주차() {
        // 2026-10-26(월)~11-01(일): 목요일이 10월 29일
        assertThat(WeekPeriod.of(LocalDate.of(2026, 11, 1)).label()).isEqualTo("10월 5주차");
    }

    @Test
    void 해가_바뀌는_주() {
        // 2026-12-28(월)~2027-01-03(일): 목요일이 12월 31일
        assertThat(WeekPeriod.of(LocalDate.of(2027, 1, 2)).label()).isEqualTo("12월 5주차");
    }
}