package com.ori_leibo.nbafantasyscout.trend;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ori_leibo.nbafantasyscout.domain.StatLine;
import com.ori_leibo.nbafantasyscout.trend.FormClassifier.Form;
import java.util.Optional;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class FormClassifierTest {

    private static final StatLine ANY = new StatLine(30, 5, 10, 2, 3, 4, 6, 4, 1, 1, 2, 15);
    private static final Optional<Trend> ESTABLISHED = Optional.of(new Trend(ANY, ANY, Trend.Baseline.THIS_SEASON));
    private static final Optional<Trend> EARLY = Optional.of(new Trend(ANY, ANY, Trend.Baseline.LAST_SEASON));
    private static final OptionalDouble HIGH_VALUE = OptionalDouble.of(4);
    private static final OptionalDouble LOW_VALUE = OptionalDouble.of(-1);

    // hot at +/-0.15, high value from 3; the hot score is fixed per case through a lambda strategy
    private static FormClassifier withHotScore(double hotScore) {
        return new FormClassifier(trend -> hotScore, 0.15, 3);
    }

    @Test
    void establishedTrendIsJudgedByHotScoreAlone() {
        assertEquals(Form.HOT, withHotScore(0.3).classify(ESTABLISHED, LOW_VALUE, true));
        assertEquals(Form.COLD, withHotScore(-0.3).classify(ESTABLISHED, HIGH_VALUE, true));
        assertEquals(Form.GOOD, withHotScore(0.0).classify(ESTABLISHED, HIGH_VALUE, true));
        assertEquals(Form.NEUTRAL, withHotScore(0.0).classify(ESTABLISHED, LOW_VALUE, true));
    }

    @Test
    void earlyRiseNeedsHighValueToBeHot() {
        assertEquals(Form.EARLY_HOT, withHotScore(0.3).classify(EARLY, HIGH_VALUE, true));
        assertEquals(Form.NEUTRAL, withHotScore(0.3).classify(EARLY, LOW_VALUE, true));
        assertEquals(Form.EARLY_COLD, withHotScore(-0.3).classify(EARLY, HIGH_VALUE, true));
        assertEquals(Form.GOOD, withHotScore(0.0).classify(EARLY, HIGH_VALUE, true));
    }

    @Test
    void withoutATrendOnlyARookieWithHighValueIsHot() {
        FormClassifier classifier = withHotScore(0);

        assertEquals(Form.EARLY_HOT, classifier.classify(Optional.empty(), HIGH_VALUE, false));
        assertEquals(Form.GOOD, classifier.classify(Optional.empty(), HIGH_VALUE, true));
        assertEquals(Form.NEUTRAL, classifier.classify(Optional.empty(), LOW_VALUE, false));
        assertEquals(Form.NEUTRAL, classifier.classify(Optional.empty(), OptionalDouble.empty(), false));
    }
}
