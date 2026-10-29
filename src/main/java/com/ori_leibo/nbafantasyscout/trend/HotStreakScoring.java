package com.ori_leibo.nbafantasyscout.trend;

import com.ori_leibo.nbafantasyscout.domain.StatLine;

/**
 * Scores growth in role, not lucky shooting: minutes, shot attempts and opportunity
 * ((FGA + FTA + TO) per minute, a usage-rate stand-in). Each uses relative change so units don't matter.
 */
public class HotStreakScoring implements ScoringStrategy {

    private final double minutesWeight;
    private final double fieldGoalsAttemptedWeight;
    private final double opportunityWeight;

    public HotStreakScoring(double minutesWeight, double fieldGoalsAttemptedWeight, double opportunityWeight) {
        this.minutesWeight = minutesWeight;
        this.fieldGoalsAttemptedWeight = fieldGoalsAttemptedWeight;
        this.opportunityWeight = opportunityWeight;
    }

    @Override
    public double score(Trend trend) {
        StatLine recent = trend.recent();
        StatLine baseline = trend.baseline();
        return minutesWeight * relativeChange(recent.minutes(), baseline.minutes())
                + fieldGoalsAttemptedWeight * relativeChange(recent.fieldGoalsAttempted(), baseline.fieldGoalsAttempted())
                + opportunityWeight * relativeChange(opportunity(recent), opportunity(baseline));
    }

    private static double opportunity(StatLine line) {
        return line.minutes() == 0 ? 0
                : (line.fieldGoalsAttempted() + line.freeThrowsAttempted() + line.turnovers()) / line.minutes();
    }

    // TODO: a zero baseline counts as no change, so a jump from 0 is ignored; revisit if real data shows such players
    private static double relativeChange(double recent, double baseline) {
        return baseline == 0 ? 0 : (recent - baseline) / baseline;
    }
}
