package com.ori_leibo.nbafantasyscout.trend;

import com.ori_leibo.nbafantasyscout.domain.StatCategory;

/** Scores one Yahoo category's delta; flipped for turnovers so improving is always positive. */
public class CategoryScoring implements ScoringStrategy {

    private final StatCategory category;

    public CategoryScoring(StatCategory category) {
        this.category = category;
    }

    @Override
    public double score(Trend trend) {
        double delta = trend.delta(category);
        return category.lowerIsBetter() ? -delta : delta;
    }
}
