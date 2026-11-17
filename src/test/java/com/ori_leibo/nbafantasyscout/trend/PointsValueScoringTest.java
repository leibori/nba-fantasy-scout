package com.ori_leibo.nbafantasyscout.trend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ori_leibo.nbafantasyscout.domain.LeagueScoring;
import com.ori_leibo.nbafantasyscout.domain.PointStat;
import com.ori_leibo.nbafantasyscout.domain.StatLine;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PointsValueScoringTest {

    // a typical Yahoo points league
    private static final LeagueScoring.Points POINTS = new LeagueScoring.Points(Map.of(
            PointStat.PTS, 1.0, PointStat.REB, 1.2, PointStat.AST, 1.5,
            PointStat.STL, 3.0, PointStat.BLK, 3.0, PointStat.TO, -1.0));

    // per-game line: FGM/FGA, 3PM, FTM/FTA, REB, AST, STL, BLK, TO, PTS
    private static StatLine line(double fgm, double fga, double threes, double ftm, double fta,
                                 double reb, double ast, double stl, double blk, double to, double pts) {
        return new StatLine(30, fgm, fga, threes, ftm, fta, reb, ast, stl, blk, to, pts);
    }

    private static final StatLine LOW = line(4, 10, 1, 2, 4, 4, 2, 0.5, 0.3, 1, 11);
    private static final StatLine MID = line(5, 10, 2, 3, 4, 6, 4, 1.0, 0.6, 2, 15);
    private static final StatLine HIGH = line(6, 10, 3, 4, 4, 8, 6, 1.5, 0.9, 3, 19);
    private static final List<StatLine> LEAGUE = List.of(LOW, MID, HIGH);

    private final PointsValueScoring scoring = new PointsValueScoring(LEAGUE, POINTS);

    @Test
    void fantasyPointsAddUpEachStatTimesItsPointValue() {
        // 15 + 6*1.2 + 4*1.5 + 1*3 + 0.6*3 - 2 = 31.0
        assertEquals(31.0, POINTS.fantasyPoints(MID), 1e-9);
    }

    @Test
    void averagePlayerIsWorthZeroAndBetterPlayersMore() {
        assertEquals(0, scoring.score(MID), 1e-9);
        assertTrue(scoring.score(HIGH) > 0);
        assertTrue(scoring.score(LOW) < 0);
    }

    @Test
    void rankingFollowsThePointValuesNotTheCategories() {
        // the scorer wins 9-cat (PTS, 3PM, FG%, fewer TO); the big man's blocks and steals at 3 points each win here
        StatLine scorer = line(9, 18, 3, 4, 4, 4, 3, 0.5, 0.2, 1, 25);  // 25 + 4.8 + 4.5 + 1.5 + 0.6 - 1 = 35.4
        StatLine bigMan = line(5, 12, 0, 2, 4, 10, 2, 1.5, 2.5, 2, 12); // 12 + 12 + 3 + 4.5 + 7.5 - 2 = 37.0
        List<StatLine> league = List.of(scorer, bigMan, MID);

        assertTrue(new PointsValueScoring(league, POINTS).score(bigMan)
                > new PointsValueScoring(league, POINTS).score(scorer));
        assertTrue(new CategoryValueScoring(league, 1.0).score(scorer)
                > new CategoryValueScoring(league, 1.0).score(bigMan));
    }

    @Test
    void identicalLeagueGivesZeroInsteadOfNaN() {
        assertEquals(0, new PointsValueScoring(List.of(MID, MID), POINTS).score(HIGH));
    }

    @Test
    void makesAndAttemptsRewardEfficientShooting() {
        LeagueScoring.Points shooting = new LeagueScoring.Points(Map.of(
                PointStat.PTS, 1.0, PointStat.FGM, 1.0, PointStat.FGA, -0.5));
        StatLine efficient = line(10, 15, 0, 0, 0, 0, 0, 0, 0, 0, 20);   // 20 + 10 - 7.5 = 22.5
        StatLine inefficient = line(10, 25, 0, 0, 0, 0, 0, 0, 0, 0, 20); // 20 + 10 - 12.5 = 17.5

        assertEquals(22.5, shooting.fantasyPoints(efficient), 1e-9);
        assertEquals(17.5, shooting.fantasyPoints(inefficient), 1e-9);
    }
}
