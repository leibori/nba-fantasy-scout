package com.ori_leibo.nbafantasyscout.domain;

import java.time.LocalDate;

/** One player's raw box score for one game. */
public record GameLog(
        LocalDate gameDate,
        int minutes,
        int fieldGoalsMade, int fieldGoalsAttempted,
        int threesMade,
        int freeThrowsMade, int freeThrowsAttempted,
        int rebounds, int assists, int steals, int blocks, int turnovers, int points) {

    // seasons run August to July, named by the year they end (2027 = 2026-27), as ESPN counts them
    private static final int FIRST_MONTH = 8;

    public int season() {
        return gameDate.getMonthValue() >= FIRST_MONTH ? gameDate.getYear() + 1 : gameDate.getYear();
    }

    public static LocalDate seasonStart(int season) {
        return LocalDate.of(season - 1, FIRST_MONTH, 1);
    }

    public static LocalDate seasonEnd(int season) {
        return seasonStart(season + 1).minusDays(1);
    }
}
