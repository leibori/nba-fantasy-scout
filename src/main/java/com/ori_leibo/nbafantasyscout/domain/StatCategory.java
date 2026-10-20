package com.ori_leibo.nbafantasyscout.domain;

import java.util.function.ToDoubleFunction;

/** Yahoo H2H 9-cat categories. */
public enum StatCategory {
    FG_PCT(StatLine::fgPct, false),
    FT_PCT(StatLine::ftPct, false),
    THREES(StatLine::fg3m, false),
    PTS(StatLine::pts, false),
    REB(StatLine::reb, false),
    AST(StatLine::ast, false),
    STL(StatLine::stl, false),
    BLK(StatLine::blk, false),
    TO(StatLine::tov, true);

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
