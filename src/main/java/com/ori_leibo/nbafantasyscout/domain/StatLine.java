package com.ori_leibo.nbafantasyscout.domain;

import java.util.List;
import java.util.function.ToIntFunction;

/** Per-game averages over a set of games. */
public record StatLine(
        double minutes,
        double fieldGoalsMade, double fieldGoalsAttempted,
        double threesMade,
        double freeThrowsMade, double freeThrowsAttempted,
        double rebounds, double assists, double steals, double blocks, double turnovers, double points) {

    public static StatLine average(List<GameLog> games) {
        if (games.isEmpty()) {
            throw new IllegalArgumentException("Cannot average zero games");
        }
        return new StatLine(
                avg(games, GameLog::minutes),
                avg(games, GameLog::fieldGoalsMade), avg(games, GameLog::fieldGoalsAttempted),
                avg(games, GameLog::threesMade),
                avg(games, GameLog::freeThrowsMade), avg(games, GameLog::freeThrowsAttempted),
                avg(games, GameLog::rebounds), avg(games, GameLog::assists), avg(games, GameLog::steals),
                avg(games, GameLog::blocks), avg(games, GameLog::turnovers), avg(games, GameLog::points));
    }

    // Ratio of averages equals total makes / total attempts, so percentages are volume-weighted.
    public double fieldGoalPct() {
        return fieldGoalsAttempted == 0 ? 0 : fieldGoalsMade / fieldGoalsAttempted;
    }

    public double freeThrowPct() {
        return freeThrowsAttempted == 0 ? 0 : freeThrowsMade / freeThrowsAttempted;
    }

    private static double avg(List<GameLog> games, ToIntFunction<GameLog> stat) {
        return games.stream().mapToInt(stat).average().orElseThrow();
    }
}
