package com.ori_leibo.nbafantasyscout.trend;

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Turns "better than before?" (the trend's hot score) and "good right now?" (the value) into one label.
 * Early in a season (provisional trend, or no trend yet) a rise only counts when the player is also good,
 * and a drop only when he had a real role, so bench players moving a few minutes are not called hot or cold.
 */
public class FormClassifier {

    public enum Form { HOT, EARLY_HOT, RETURNING, GOOD, NEUTRAL, EARLY_COLD, COLD }

    /** What the player did before this season; matters only while he has no trend yet. */
    public enum History { PLAYED_LAST_SEASON, ROOKIE, RETURNING }

    private final ScoringStrategy hotScoring;
    private final double hotThreshold;
    private final double highValue;
    private final double earlyColdMinMinutes;

    public FormClassifier(ScoringStrategy hotScoring, double hotThreshold, double highValue, double earlyColdMinMinutes) {
        this.hotScoring = hotScoring;
        this.hotThreshold = hotThreshold;
        this.highValue = highValue;
        this.earlyColdMinMinutes = earlyColdMinMinutes;
    }

    /** {@code value} is this season's value, empty when the player has no games yet. */
    public Form classify(Optional<Trend> trend, OptionalDouble value, History history) {
        boolean high = value.isPresent() && value.getAsDouble() >= highValue;
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
        double score = hotScoring.score(current);
        boolean early = current.provisional();
        if (score >= hotThreshold && (!early || high)) {
            return early ? Form.EARLY_HOT : Form.HOT;
        }
        if (score <= -hotThreshold && (!early || current.baseline().minutes() >= earlyColdMinMinutes)) {
            return early ? Form.EARLY_COLD : Form.COLD;
        }
        return high ? Form.GOOD : Form.NEUTRAL;
    }
}
