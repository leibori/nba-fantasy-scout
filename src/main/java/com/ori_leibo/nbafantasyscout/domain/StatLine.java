package com.ori_leibo.nbafantasyscout.domain;

import java.util.List;
import java.util.function.ToIntFunction;

/** Per-game averages over a set of games. */
public record StatLine(
        double minutes,
        double fgm, double fga,
        double fg3m,
        double ftm, double fta,
        double reb, double ast, double stl, double blk, double tov, double pts) {

    public static StatLine average(List<GameLog> games) {
        if (games.isEmpty()) {
            throw new IllegalArgumentException("Cannot average zero games");
        }
        return new StatLine(
                avg(games, GameLog::minutes),
                avg(games, GameLog::fgm), avg(games, GameLog::fga),
                avg(games, GameLog::fg3m),
                avg(games, GameLog::ftm), avg(games, GameLog::fta),
                avg(games, GameLog::reb), avg(games, GameLog::ast), avg(games, GameLog::stl),
                avg(games, GameLog::blk), avg(games, GameLog::tov), avg(games, GameLog::pts));
    }

    // Ratio of averages equals total makes / total attempts, so percentages are volume-weighted.
    public double fgPct() {
        return fga == 0 ? 0 : fgm / fga;
    }

    public double ftPct() {
        return fta == 0 ? 0 : ftm / fta;
    }

    private static double avg(List<GameLog> games, ToIntFunction<GameLog> stat) {
        return games.stream().mapToInt(stat).average().orElseThrow();
    }
}
