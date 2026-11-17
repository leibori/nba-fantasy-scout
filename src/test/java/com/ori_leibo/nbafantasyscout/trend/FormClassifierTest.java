package com.ori_leibo.nbafantasyscout.trend;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ori_leibo.nbafantasyscout.domain.StatLine;
import com.ori_leibo.nbafantasyscout.trend.FormClassifier.Form;
import com.ori_leibo.nbafantasyscout.trend.FormClassifier.History;
import java.util.Optional;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class FormClassifierTest {

    // hot at +/-0.15, high value from 3, early cold needs 18 baseline minutes and a 15% minutes drop
    private static final FormClassifier CLASSIFIER = new FormClassifier(0.15, 3, 18, 0.15);

    private static final StatLine STARTER = line(30);
    private static final Optional<Trend> ESTABLISHED = Optional.of(new Trend(STARTER, STARTER, Trend.Baseline.THIS_SEASON));
    private static final Optional<Trend> EARLY = Optional.of(new Trend(STARTER, STARTER, Trend.Baseline.LAST_SEASON));
    private static final OptionalDouble HIGH_VALUE = OptionalDouble.of(4);
    private static final OptionalDouble LOW_VALUE = OptionalDouble.of(-1);
    private static final OptionalDouble RISING = OptionalDouble.of(0.3);
    private static final OptionalDouble FLAT = OptionalDouble.of(0.0);
    private static final OptionalDouble FALLING = OptionalDouble.of(-0.3);

    private static StatLine line(double minutes) {
        return new StatLine(minutes, 5, 10, 2, 3, 4, 6, 4, 1, 1, 2, 15);
    }

    @Test
    void establishedTrendIsJudgedByHotScoreAlone() {
        assertEquals(Form.HOT, CLASSIFIER.classify(ESTABLISHED, RISING, LOW_VALUE, History.PLAYED_LAST_SEASON));
        assertEquals(Form.COLD, CLASSIFIER.classify(ESTABLISHED, FALLING, HIGH_VALUE, History.PLAYED_LAST_SEASON));
        assertEquals(Form.GOOD, CLASSIFIER.classify(ESTABLISHED, FLAT, HIGH_VALUE, History.PLAYED_LAST_SEASON));
        assertEquals(Form.NEUTRAL, CLASSIFIER.classify(ESTABLISHED, FLAT, LOW_VALUE, History.PLAYED_LAST_SEASON));
    }

    @Test
    void earlyRiseNeedsHighValueAndEarlyDropNeedsALostRole() {
        Optional<Trend> benchBaseline = Optional.of(new Trend(line(10), line(14), Trend.Baseline.LAST_SEASON));
        Optional<Trend> lostMinutes = Optional.of(new Trend(line(24), line(30), Trend.Baseline.LAST_SEASON));
        Optional<Trend> sameMinutes = Optional.of(new Trend(line(29), line(30), Trend.Baseline.LAST_SEASON));

        assertEquals(Form.EARLY_HOT, CLASSIFIER.classify(EARLY, RISING, HIGH_VALUE, History.PLAYED_LAST_SEASON));
        assertEquals(Form.NEUTRAL, CLASSIFIER.classify(EARLY, RISING, LOW_VALUE, History.PLAYED_LAST_SEASON));
        assertEquals(Form.EARLY_COLD, CLASSIFIER.classify(lostMinutes, FALLING, LOW_VALUE, History.PLAYED_LAST_SEASON));
        assertEquals(Form.NEUTRAL, CLASSIFIER.classify(sameMinutes, FALLING, LOW_VALUE, History.PLAYED_LAST_SEASON),
                "fewer shots in the same minutes is early-season noise");
        assertEquals(Form.NEUTRAL, CLASSIFIER.classify(benchBaseline, FALLING, LOW_VALUE, History.PLAYED_LAST_SEASON));
        assertEquals(Form.COLD, CLASSIFIER.classify(
                Optional.of(new Trend(line(29), line(30), Trend.Baseline.THIS_SEASON)), FALLING, LOW_VALUE,
                History.PLAYED_LAST_SEASON), "an established trend needs no minutes drop");
        assertEquals(Form.GOOD, CLASSIFIER.classify(EARLY, FLAT, HIGH_VALUE, History.PLAYED_LAST_SEASON));
    }

    @Test
    void withoutATrendHistoryDecidesTheLabelOfAGoodStart() {
        OptionalDouble noScore = OptionalDouble.empty();

        assertEquals(Form.EARLY_HOT, CLASSIFIER.classify(Optional.empty(), noScore, HIGH_VALUE, History.ROOKIE));
        assertEquals(Form.RETURNING, CLASSIFIER.classify(Optional.empty(), noScore, HIGH_VALUE, History.RETURNING));
        assertEquals(Form.GOOD, CLASSIFIER.classify(Optional.empty(), noScore, HIGH_VALUE, History.PLAYED_LAST_SEASON));
        assertEquals(Form.NEUTRAL, CLASSIFIER.classify(Optional.empty(), noScore, LOW_VALUE, History.ROOKIE));
        assertEquals(Form.NEUTRAL, CLASSIFIER.classify(Optional.empty(), noScore, OptionalDouble.empty(), History.ROOKIE));
    }
}
