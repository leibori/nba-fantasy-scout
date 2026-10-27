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

    /** The year the season ends, as ESPN counts it: games from August on belong to the next year's season. */
    public int season() {
        return gameDate.getMonthValue() >= 8 ? gameDate.getYear() + 1 : gameDate.getYear();
    }
}
