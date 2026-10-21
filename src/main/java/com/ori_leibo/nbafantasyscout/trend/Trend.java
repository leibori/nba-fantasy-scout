package com.ori_leibo.nbafantasyscout.trend;

import com.ori_leibo.nbafantasyscout.domain.StatCategory;
import com.ori_leibo.nbafantasyscout.domain.StatLine;

/** Recent form vs. the games before it. Positive delta = the stat went up. */
public record Trend(StatLine recent, StatLine baseline) {

    public double delta(StatCategory category) {
        return category.valueOf(recent) - category.valueOf(baseline);
    }

    public double minutesDelta() {
        return recent.minutes() - baseline.minutes();
    }
}