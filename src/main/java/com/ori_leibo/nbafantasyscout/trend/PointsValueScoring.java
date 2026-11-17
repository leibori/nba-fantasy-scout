package com.ori_leibo.nbafantasyscout.trend;

import com.ori_leibo.nbafantasyscout.domain.LeagueScoring;
import com.ori_leibo.nbafantasyscout.domain.StatLine;
import java.util.List;

/**
 * Value in a points league: a player's fantasy points per game as a z-score against the league,
 * so "how far above an average player" reads the same way as in a 9-cat league.
 */
public class PointsValueScoring implements ValueScoring {

    private final LeagueScoring.Points points;
    private final double mean;
    private final double stdDev;

    public PointsValueScoring(List<StatLine> league, LeagueScoring.Points points) {
        if (league.isEmpty()) {
            throw new IllegalArgumentException("Cannot score against an empty league");
        }
        this.points = points;
        double[] stats = CategoryValueScoring.meanAndStdDev(league, points::fantasyPoints);
        this.mean = stats[0];
        this.stdDev = stats[1];
    }

    @Override
    public double score(StatLine line) {
        // everyone scores the same: points can't separate players (and no NaN)
        return stdDev == 0 ? 0 : (points.fantasyPoints(line) - mean) / stdDev;
    }
}
