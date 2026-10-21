package com.ori_leibo.nbafantasyscout.domain;

import java.util.function.ToDoubleFunction;

/** Yahoo H2H 9-cat categories. */
public enum StatCategory {
    FG_PCT(StatLine::fieldGoalPct, false),
    FT_PCT(StatLine::freeThrowPct, false),
    THREES(StatLine::threesMade, false),
    PTS(StatLine::points, false),
    REB(StatLine::rebounds, false),
    AST(StatLine::assists, false),
    STL(StatLine::steals, false),
    BLK(StatLine::blocks, false),
    TO(StatLine::turnovers, true);

    private final ToDoubleFunction<StatLine> extractor;
    private final boolean lowerIsBetter;

    StatCategory(ToDoubleFunction<StatLine> extractor, boolean lowerIsBetter) {
        this.extractor = extractor;
        this.lowerIsBetter = lowerIsBetter;
    }

    public double valueOf(StatLine line) {
        return extractor.applyAsDouble(line);
    }

    public boolean lowerIsBetter() {
        return lowerIsBetter;
    }
}
