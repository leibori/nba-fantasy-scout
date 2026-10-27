package com.ori_leibo.nbafantasyscout.trend;

import com.ori_leibo.nbafantasyscout.domain.GameLog;
import com.ori_leibo.nbafantasyscout.domain.StatLine;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Compares a player's last {@code windowSize} games with a baseline: the games before them this season,
 * or last season when this season has fewer than {@code minBaselineGames} earlier games.
 */
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

    /**
     * Empty when the player is noise: too few games this season for the window, no baseline anywhere
     * (a rookie early on; the value score covers those), or low minutes in both periods.
     */
    public Optional<Trend> calculate(List<GameLog> thisSeason, List<GameLog> lastSeason) {
        if (thisSeason.size() < windowSize) {
            return Optional.empty();
        }
        List<GameLog> sorted = thisSeason.stream().sorted(Comparator.comparing(GameLog::gameDate)).toList();
        int split = sorted.size() - windowSize;
        StatLine recent = StatLine.average(sorted.subList(split, sorted.size()));

        StatLine baseline;
        Trend.Baseline source;
        if (split >= minBaselineGames) {
            baseline = StatLine.average(sorted.subList(0, split));
            source = Trend.Baseline.THIS_SEASON;
        } else if (lastSeason.size() >= minBaselineGames) {
            baseline = StatLine.average(lastSeason);
            source = Trend.Baseline.LAST_SEASON;
        } else {
            return Optional.empty();
        }

        // Either period counts, so a starter who lost minutes still shows up as a decline.
        if (Math.max(recent.minutes(), baseline.minutes()) < minMinutes) {
            return Optional.empty();
        }
        return Optional.of(new Trend(recent, baseline, source));
    }
}
