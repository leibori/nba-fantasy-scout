package com.ori_leibo.nbafantasyscout.trend;

/** Turns a trend into one number for ranking. Higher = hotter. */
public interface ScoringStrategy {

    double score(Trend trend);
}
