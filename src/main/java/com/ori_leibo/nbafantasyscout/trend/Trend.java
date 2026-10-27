package com.ori_leibo.nbafantasyscout.trend;

import com.ori_leibo.nbafantasyscout.domain.StatCategory;
import com.ori_leibo.nbafantasyscout.domain.StatLine;

/** Recent form vs. a baseline. Positive delta = the stat went up. */
public record Trend(StatLine recent, StatLine baseline, Baseline baselineSource) {

    /** Where the baseline came from: this season's earlier games, or last season when this season is too young. */
    public enum Baseline { THIS_SEASON, LAST_SEASON }

    public double delta(StatCategory category) {
        return category.valueOf(recent) - category.valueOf(baseline);
    }

    public double minutesDelta() {
        return recent.minutes() - baseline.minutes();
    }

    /** A last-season baseline is a weaker claim: the user sees it, and it is re-judged once this season has enough games. */
    public boolean provisional() {
        return baselineSource == Baseline.LAST_SEASON;
    }
}
