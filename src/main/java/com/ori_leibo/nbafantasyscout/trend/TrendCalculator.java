package com.ori_leibo.nbafantasyscout.trend;

import com.ori_leibo.nbafantasyscout.domain.GameLog;
import com.ori_leibo.nbafantasyscout.domain.StatLine;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Compares a player's last {@code windowSize} games with the games before them. */
public class TrendCalculator {

    private final int windowSize;
    private final int minBaselineGames;
    private final double minMinutes;

    public TrendCalculator(int windowSize, int minBaselineGames, double minMinutes) {
        if (windowSize < 1 || minBaselineGames < 1 || minMinutes < 0) {
            throw new IllegalArgumentException("Invalid trend settings: window=%d, minBaselineGames=%d, minMinutes=%s"
                    .formatted(windowSize, minBaselineGames, minMinutes));
        }
        this.windowSize = windowSize;
        this.minBaselineGames = minBaselineGames;
        this.minMinutes = minMinutes;
    }

    /** Empty when the player is noise: too few games, or low minutes in both periods. */
    public Optional<Trend> calculate(List<GameLog> games) {
        if (games.size() < windowSize + minBaselineGames) {
            return Optional.empty();
        }
        List<GameLog> sorted = games.stream().sorted(Comparator.comparing(GameLog::gameDate)).toList();
        int split = sorted.size() - windowSize;
        StatLine baseline = StatLine.average(sorted.subList(0, split));
        StatLine recent = StatLine.average(sorted.subList(split, sorted.size()));

        // Either period counts, so a starter who lost minutes still shows up as a decline.
        if (Math.max(recent.minutes(), baseline.minutes()) < minMinutes) {
            return Optional.empty();
        }
        return Optional.of(new Trend(recent, baseline));
    }
}
