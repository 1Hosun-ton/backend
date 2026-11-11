package com.hosunton.backend.domain.home;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

public record WeekPeriod(LocalDate startDate, LocalDate endDate, String label) {

    public static WeekPeriod of(LocalDate date) {
        LocalDate monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate thursday = monday.plusDays(3);
        int weekOfMonth = (thursday.getDayOfMonth() - 1) / 7 + 1;
        String label = thursday.getMonthValue() + "월 " + weekOfMonth + "주차";
        return new WeekPeriod(monday, monday.plusDays(6), label);
    }
}