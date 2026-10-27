package com.ori_leibo.nbafantasyscout.trend;

import com.ori_leibo.nbafantasyscout.domain.StatCategory;
import com.ori_leibo.nbafantasyscout.domain.StatLine;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/**
 * "Is he good right now?": the sum of a player's 9-cat z-scores against a league of per-game lines.
 * Needs no baseline, so it also rates rookies. Not a {@link ScoringStrategy}: it scores a stat line, not a trend.
 */
public class ValueScoring {

    private final double percentageWeight;
    private final Map<StatCategory, ToDoubleFunction<StatLine>> measures = new EnumMap<>(StatCategory.class);
    private final Map<StatCategory, double[]> meanAndStdDev = new EnumMap<>(StatCategory.class);

    /** {@code percentageWeight} scales FG% and FT%: low early in a season, when shooting is mostly noise. */
    public ValueScoring(List<StatLine> league, double percentageWeight) {
        if (league.isEmpty()) {
            throw new IllegalArgumentException("Cannot score against an empty league");
        }
        this.percentageWeight = percentageWeight;

        // a percentage is rated by its impact, makes above what a league-average shooter makes on the same attempts,
        // so 60% on 20 shots outweighs 100% on 1
        double leagueFgPct = sum(league, StatLine::fieldGoalsMade) / sum(league, StatLine::fieldGoalsAttempted);
        double leagueFtPct = sum(league, StatLine::freeThrowsMade) / sum(league, StatLine::freeThrowsAttempted);
        for (StatCategory category : StatCategory.values()) {
            measures.put(category, switch (category) {
                case FG_PCT -> line -> line.fieldGoalsMade() - leagueFgPct * line.fieldGoalsAttempted();
                case FT_PCT -> line -> line.freeThrowsMade() - leagueFtPct * line.freeThrowsAttempted();
                default -> category::valueOf;
            });
            meanAndStdDev.put(category, meanAndStdDev(league, measures.get(category)));
        }
    }

    public double score(StatLine line) {
        double value = 0;
        for (StatCategory category : StatCategory.values()) {
            double[] stats = meanAndStdDev.get(category);
            // everyone equal in a category: it can't separate players, so it adds nothing (and no NaN)
            double z = stats[1] == 0 ? 0 : (measures.get(category).applyAsDouble(line) - stats[0]) / stats[1];
            double signed = category.lowerIsBetter() ? -z : z;
            boolean percentage = category == StatCategory.FG_PCT || category == StatCategory.FT_PCT;
            value += percentage ? percentageWeight * signed : signed;
        }
        return value;
    }

    private static double sum(List<StatLine> league, ToDoubleFunction<StatLine> stat) {
        return league.stream().mapToDouble(stat).sum();
    }

    // population standard deviation: the league is the whole population, not a sample of it
    private static double[] meanAndStdDev(List<StatLine> league, ToDoubleFunction<StatLine> measure) {
        double mean = league.stream().mapToDouble(measure).average().orElseThrow();
        double variance = league.stream().mapToDouble(measure).map(v -> (v - mean) * (v - mean)).average().orElseThrow();
        return new double[] {mean, Math.sqrt(variance)};
    }
}
