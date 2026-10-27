package com.ori_leibo.nbafantasyscout.trend;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ori_leibo.nbafantasyscout.domain.StatLine;
import org.junit.jupiter.api.Test;

class HotStreakScoringTest {

    private final HotStreakScoring scoring = new HotStreakScoring(1, 1, 1);

    private static StatLine line(double minutes, double fga, double fta, double turnovers) {
        return new StatLine(minutes, 0, fga, 0, 0, fta, 0, 0, 0, 0, turnovers, 0);
    }

    @Test
    void roleGrowthScoresPositiveAndShrinkingRoleNegative() {
        StatLine bench = line(20, 8, 2, 2);  // opportunity 12 / 20 = 0.6
        StatLine starter = line(30, 16, 4, 4); // opportunity 24 / 30 = 0.8

        // minutes +50%, FGA +100%, opportunity +33.3%
        assertEquals(0.5 + 1.0 + 1.0 / 3, scoring.score(new Trend(starter, bench, Trend.Baseline.THIS_SEASON)), 1e-9);
        assertEquals(-1.0 / 3 - 0.5 - 0.25, scoring.score(new Trend(bench, starter, Trend.Baseline.THIS_SEASON)), 1e-9);
    }

    @Test
    void zeroBaselineCountsAsNoChange() {
        StatLine nothing = line(0, 0, 0, 0);

        assertEquals(0, scoring.score(new Trend(line(30, 16, 4, 4), nothing, Trend.Baseline.THIS_SEASON)));
    }
}
