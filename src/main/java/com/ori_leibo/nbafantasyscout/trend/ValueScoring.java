package com.ori_leibo.nbafantasyscout.trend;

import com.ori_leibo.nbafantasyscout.domain.StatLine;

/**
 * "Is he good right now?": a player's per-game line rated against the league, in the league's scoring format.
 * Needs no baseline, so it also rates rookies. Not a {@link ScoringStrategy}: it scores a stat line, not a trend.
 */
public interface ValueScoring {

    double score(StatLine line);
}
