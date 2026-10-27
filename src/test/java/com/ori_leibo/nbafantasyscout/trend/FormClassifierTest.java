package com.ori_leibo.nbafantasyscout.trend;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ori_leibo.nbafantasyscout.domain.StatLine;
import com.ori_leibo.nbafantasyscout.trend.FormClassifier.Form;
import com.ori_leibo.nbafantasyscout.trend.FormClassifier.History;
import java.util.Optional;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class FormClassifierTest {

    private static final StatLine STARTER = line(30);
    private static final Optional<Trend> ESTABLISHED = Optional.of(new Trend(STARTER, STARTER, Trend.Baseline.THIS_SEASON));
    private static final Optional<Trend> EARLY = Optional.of(new Trend(STARTER, STARTER, Trend.Baseline.LAST_SEASON));
    private static final OptionalDouble HIGH_VALUE = OptionalDouble.of(4);
    private static final OptionalDouble LOW_VALUE = OptionalDouble.of(-1);

    private static StatLine line(double minutes) {
        return new StatLine(minutes, 5, 10, 2, 3, 4, 6, 4, 1, 1, 2, 15);
    }

    // hot at +/-0.15, high value from 3, early cold needs 18 baseline minutes; the hot score is fixed per case
    private static FormClassifier withHotScore(double hotScore) {
        return new FormClassifier(trend -> hotScore, 0.15, 3, 18);
    }

    @Test
    void establishedTrendIsJudgedByHotScoreAlone() {
        assertEquals(Form.HOT, withHotScore(0.3).classify(ESTABLISHED, LOW_VALUE, History.PLAYED_LAST_SEASON));
        assertEquals(Form.COLD, withHotScore(-0.3).classify(ESTABLISHED, HIGH_VALUE, History.PLAYED_LAST_SEASON));
        assertEquals(Form.GOOD, withHotScore(0.0).classify(ESTABLISHED, HIGH_VALUE, History.PLAYED_LAST_SEASON));
        assertEquals(Form.NEUTRAL, withHotScore(0.0).classify(ESTABLISHED, LOW_VALUE, History.PLAYED_LAST_SEASON));
    }

    @Test
    void earlyRiseNeedsHighValueAndEarlyDropNeedsARealRole() {
        Optional<Trend> benchBaseline = Optional.of(new Trend(line(10), line(14), Trend.Baseline.LAST_SEASON));

        assertEquals(Form.EARLY_HOT, withHotScore(0.3).classify(EARLY, HIGH_VALUE, History.PLAYED_LAST_SEASON));
        assertEquals(Form.NEUTRAL, withHotScore(0.3).classify(EARLY, LOW_VALUE, History.PLAYED_LAST_SEASON));
        assertEquals(Form.EARLY_COLD, withHotScore(-0.3).classify(EARLY, LOW_VALUE, History.PLAYED_LAST_SEASON));
        assertEquals(Form.NEUTRAL, withHotScore(-0.3).classify(benchBaseline, LOW_VALUE, History.PLAYED_LAST_SEASON));
        assertEquals(Form.GOOD, withHotScore(0.0).classify(EARLY, HIGH_VALUE, History.PLAYED_LAST_SEASON));
    }

    @Test
    void withoutATrendHistoryDecidesTheLabelOfAGoodStart() {
        FormClassifier classifier = withHotScore(0);

        assertEquals(Form.EARLY_HOT, classifier.classify(Optional.empty(), HIGH_VALUE, History.ROOKIE));
        assertEquals(Form.RETURNING, classifier.classify(Optional.empty(), HIGH_VALUE, History.RETURNING));
        assertEquals(Form.GOOD, classifier.classify(Optional.empty(), HIGH_VALUE, History.PLAYED_LAST_SEASON));
        assertEquals(Form.NEUTRAL, classifier.classify(Optional.empty(), LOW_VALUE, History.ROOKIE));
        assertEquals(Form.NEUTRAL, classifier.classify(Optional.empty(), OptionalDouble.empty(), History.ROOKIE));
    }
}
