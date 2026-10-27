package com.ori_leibo.nbafantasyscout.trend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ori_leibo.nbafantasyscout.domain.StatLine;
import java.util.List;
import org.junit.jupiter.api.Test;

class ValueScoringTest {

    // per-game line: FGM/FGA, 3PM, FTM/FTA, REB, AST, STL, BLK, TO, PTS
    private static StatLine line(double fgm, double fga, double threes, double ftm, double fta,
                                 double reb, double ast, double stl, double blk, double to, double pts) {
        return new StatLine(30, fgm, fga, threes, ftm, fta, reb, ast, stl, blk, to, pts);
    }

    private static final StatLine LOW = line(4, 10, 1, 2, 4, 4, 2, 0.5, 0.3, 1, 11);
    private static final StatLine MID = line(5, 10, 2, 3, 4, 6, 4, 1.0, 0.6, 2, 15);
    private static final StatLine HIGH = line(6, 10, 3, 4, 4, 8, 6, 1.5, 0.9, 3, 19);
    private static final List<StatLine> LEAGUE = List.of(LOW, MID, HIGH);

    private final ValueScoring scoring = new ValueScoring(LEAGUE, 1.0);

    @Test
    void averagePlayerIsWorthZeroAndBetterPlayersMore() {
        assertEquals(0, scoring.score(MID), 1e-9);
        assertTrue(scoring.score(HIGH) > 0);
        assertTrue(scoring.score(LOW) < 0);
    }

    @Test
    void turnoversCountAgainstAPlayer() {
        StatLine careless = line(5, 10, 2, 3, 4, 6, 4, 1.0, 0.6, 3, 15);

        // MID with one more turnover; league TO is 1, 2, 3: std dev sqrt(2/3), so the value drops by 1 / sqrt(2/3)
        assertEquals(-1 / Math.sqrt(2.0 / 3), scoring.score(careless), 1e-9);
    }

    @Test
    void shootingPercentageIsWeightedByVolume() {
        StatLine oneLuckyShot = line(1, 1, 2, 3, 4, 6, 4, 1.0, 0.6, 2, 15);    // 100% on 1 shot
        StatLine efficientVolume = line(12, 20, 2, 3, 4, 6, 4, 1.0, 0.6, 2, 15); // 60% on 20 shots

        assertTrue(scoring.score(efficientVolume) > scoring.score(oneLuckyShot));
    }

    @Test
    void percentageWeightScalesOnlyTheShootingCategories() {
        StatLine sharpShooter = line(8, 10, 2, 4, 4, 6, 4, 1.0, 0.6, 2, 15);

        double full = new ValueScoring(LEAGUE, 1.0).score(sharpShooter);
        double half = new ValueScoring(LEAGUE, 0.5).score(sharpShooter);

        // every counting stat equals MID, so the whole value comes from FG% and FT%
        assertEquals(full / 2, half, 1e-9);
    }

    @Test
    void identicalLeagueGivesZeroInsteadOfNaN() {
        assertEquals(0, new ValueScoring(List.of(MID, MID), 1.0).score(HIGH));
    }
}
