package com.ori_leibo.nbafantasyscout.domain;

import java.util.function.ToDoubleFunction;

/**
 * A stat a points league can put a point value on. Counts only: a percentage can't be worth points,
 * so leagues that reward efficient shooting score makes and attempts instead (e.g. FGM +1, FGA -0.5).
 */
public enum PointStat {
    PTS(StatLine::points),
    REB(StatLine::rebounds),
    AST(StatLine::assists),
    STL(StatLine::steals),
    BLK(StatLine::blocks),
    TO(StatLine::turnovers),
    THREES(StatLine::threesMade),
    FGM(StatLine::fieldGoalsMade),
    FGA(StatLine::fieldGoalsAttempted),
    FTM(StatLine::freeThrowsMade),
    FTA(StatLine::freeThrowsAttempted);

    private final ToDoubleFunction<StatLine> extractor;

    PointStat(ToDoubleFunction<StatLine> extractor) {
        this.extractor = extractor;
    }

    public double valueOf(StatLine line) {
        return extractor.applyAsDouble(line);
    }
}
