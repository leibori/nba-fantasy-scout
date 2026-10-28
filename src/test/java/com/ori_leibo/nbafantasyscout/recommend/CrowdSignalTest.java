package com.ori_leibo.nbafantasyscout.recommend;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ori_leibo.nbafantasyscout.recommend.CrowdSignal.Crowd;
import com.ori_leibo.nbafantasyscout.trend.FormClassifier.Form;
import org.junit.jupiter.api.Test;

class CrowdSignalTest {

    @Test
    void risingPlayer() {
        for (Form up : new Form[] {Form.HOT, Form.EARLY_HOT, Form.RETURNING}) {
            assertEquals(CrowdSignal.CONFIRMED_BREAKOUT, CrowdSignal.of(up, Crowd.ADDS), up.name());
            assertEquals(CrowdSignal.EARLY_PICKUP, CrowdSignal.of(up, Crowd.QUIET), up.name());
            assertEquals(CrowdSignal.PANIC_DROP, CrowdSignal.of(up, Crowd.DROPS), up.name());
        }
    }

    @Test
    void decliningPlayer() {
        for (Form down : new Form[] {Form.COLD, Form.EARLY_COLD}) {
            assertEquals(CrowdSignal.HYPE, CrowdSignal.of(down, Crowd.ADDS), down.name());
            assertEquals(CrowdSignal.EARLY_WARNING, CrowdSignal.of(down, Crowd.QUIET), down.name());
            assertEquals(CrowdSignal.CONFIRMED_DECLINE, CrowdSignal.of(down, Crowd.DROPS), down.name());
        }
    }

    @Test
    void steadyPlayer() {
        assertEquals(CrowdSignal.NONE, CrowdSignal.of(Form.GOOD, Crowd.ADDS));
        assertEquals(CrowdSignal.NONE, CrowdSignal.of(Form.GOOD, Crowd.QUIET));
        assertEquals(CrowdSignal.PANIC_DROP, CrowdSignal.of(Form.GOOD, Crowd.DROPS));
        assertEquals(CrowdSignal.HYPE, CrowdSignal.of(Form.NEUTRAL, Crowd.ADDS));
        assertEquals(CrowdSignal.NONE, CrowdSignal.of(Form.NEUTRAL, Crowd.QUIET));
        assertEquals(CrowdSignal.NONE, CrowdSignal.of(Form.NEUTRAL, Crowd.DROPS));
    }
}
