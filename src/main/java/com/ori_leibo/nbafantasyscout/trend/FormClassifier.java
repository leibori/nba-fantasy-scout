package com.ori_leibo.nbafantasyscout.trend;

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Turns "better than before?" (the trend's hot score) and "good right now?" (the value) into one label.
 * Early in a season (provisional trend, or no trend yet) a rise only counts when the player is also good,
 * and a drop only when he had a real role and lost minutes: minutes settle within a few games, shot volume does not.
 */
public class FormClassifier {

    public enum Form { HOT, EARLY_HOT, RETURNING, GOOD, NEUTRAL, EARLY_COLD, COLD }

    /** What the player did before this season; matters only while he has no trend yet. */
    public enum History { PLAYED_LAST_SEASON, ROOKIE, RETURNING }

    private final double hotThreshold;
    private final double earlyColdMinMinutes;
    private final double earlyColdMinMinutesDrop;

    /** {@code earlyColdMinMinutesDrop} is a share: 0.15 = recent minutes at least 15% below the baseline. */
    public FormClassifier(double hotThreshold, double earlyColdMinMinutes, double earlyColdMinMinutesDrop) {
        this.hotThreshold = hotThreshold;
        this.earlyColdMinMinutes = earlyColdMinMinutes;
        this.earlyColdMinMinutesDrop = earlyColdMinMinutesDrop;
    }

    /**
     * {@code hotScore} is the trend's hot score, present exactly when {@code trend} is (passed in, not computed here,
     * so the label and the score shown next to it can't disagree); {@code high} = this season's value reaches the
     * league format's high-value threshold (false without games).
     */
    public Form classify(Optional<Trend> trend, OptionalDouble hotScore, boolean high, History history) {
        if (trend.isEmpty()) {
            if (!high) {
                return Form.NEUTRAL;
            }
            return switch (history) {
                case ROOKIE -> Form.EARLY_HOT;
                case RETURNING -> Form.RETURNING;
                // a veteran in his first games, playing like last season: good, not hot
                case PLAYED_LAST_SEASON -> Form.GOOD;
            };
        }

        Trend current = trend.get();
        double score = hotScore.orElseThrow(() -> new IllegalArgumentException("A trend without a hot score"));
        boolean early = current.provisional();
        if (score >= hotThreshold && (!early || high)) {
            return early ? Form.EARLY_HOT : Form.HOT;
        }
        if (score <= -hotThreshold && (!early || lostARealRole(current))) {
            return early ? Form.EARLY_COLD : Form.COLD;
        }
        return high ? Form.GOOD : Form.NEUTRAL;
    }

    private boolean lostARealRole(Trend trend) {
        double baselineMinutes = trend.baseline().minutes();
        return baselineMinutes >= earlyColdMinMinutes
                && trend.recent().minutes() <= baselineMinutes * (1 - earlyColdMinMinutesDrop);
    }
}
