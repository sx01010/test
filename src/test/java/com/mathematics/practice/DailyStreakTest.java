package com.mathematics.practice;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class DailyStreakTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);

    private static LocalDate ago(int days) {
        return TODAY.minusDays(days);
    }

    @Test
    void countsBackFromToday() {
        assertEquals(3, DailyService.streak(List.of(TODAY, ago(1), ago(2), ago(4)), TODAY));
    }

    @Test
    void notYetPracticedTodayKeepsYesterdaysStreak() {
        assertEquals(2, DailyService.streak(List.of(ago(1), ago(2)), TODAY));
    }

    @Test
    void missingYesterdayBreaksIt() {
        assertEquals(0, DailyService.streak(List.of(ago(2), ago(3)), TODAY));
        assertEquals(0, DailyService.streak(List.of(), TODAY));
    }
}
