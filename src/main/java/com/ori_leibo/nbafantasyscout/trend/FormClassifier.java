package com.ori_leibo.nbafantasyscout.trend;

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Turns "better than before?" (the trend's hot score) and "good right now?" (the value) into one label.
 * Early in a season (provisional trend, or no trend yet) a rise only counts when the player is also good,
 * so a bench player going from 8 to 14 minutes is not called hot.
 */
public class FormClassifier {

    public enum Form { HOT, EARLY_HOT, GOOD, NEUTRAL, EARLY_COLD, COLD }

    private final ScoringStrategy hotScoring;
    private final double hotThreshold;
    private final double highValue;

    public FormClassifier(ScoringStrategy hotScoring, double hotThreshold, double highValue) {
        this.hotScoring = hotScoring;
        this.hotThreshold = hotThreshold;
        this.highValue = highValue;
    }

    /**
     * @param value             this season's value; empty when the player has no games yet
     * @param playedLastSeason  separates a rookie (worth flagging at once) from a veteran in his first games
     */
    public Form classify(Optional<Trend> trend, OptionalDouble value, boolean playedLastSeason) {
        boolean high = value.isPresent() && value.getAsDouble() >= highValue;
        if (trend.isEmpty()) {
            if (!high) {
                return Form.NEUTRAL;
            }
            return playedLastSeason ? Form.GOOD : Form.EARLY_HOT;
        }

        double score = hotScoring.score(trend.get());
        boolean early = trend.get().provisional();
        if (score >= hotThreshold && (!early || high)) {
            return early ? Form.EARLY_HOT : Form.HOT;
        }
        if (score <= -hotThreshold) {
            return early ? Form.EARLY_COLD : Form.COLD;
        }
        return high ? Form.GOOD : Form.NEUTRAL;
    }
}
