package com.ori_leibo.nbafantasyscout.trend;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ori_leibo.nbafantasyscout.domain.StatCategory;
import com.ori_leibo.nbafantasyscout.domain.StatLine;
import org.junit.jupiter.api.Test;

class CategoryScoringTest {

    // recent: 4 blocks, 1 turnover; baseline: 1 block, 3 turnovers
    private final Trend trend = new Trend(
            new StatLine(30, 0, 0, 0, 0, 0, 0, 0, 0, 4, 1, 0),
            new StatLine(30, 0, 0, 0, 0, 0, 0, 0, 0, 1, 3, 0),
            Trend.Baseline.THIS_SEASON);

    @Test
    void scoresTheCategoryDelta() {
        assertEquals(3, new CategoryScoring(StatCategory.BLK).score(trend));
    }

    @Test
    void fewerTurnoversScorePositive() {
        assertEquals(2, new CategoryScoring(StatCategory.TO).score(trend));
    }
}
