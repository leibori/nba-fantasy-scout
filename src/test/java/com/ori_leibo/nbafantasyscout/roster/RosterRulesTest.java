package com.ori_leibo.nbafantasyscout.roster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.ori_leibo.nbafantasyscout.domain.Player;
import com.ori_leibo.nbafantasyscout.recommend.CrowdSignal;
import com.ori_leibo.nbafantasyscout.recommend.TrendService.PlayerForm;
import com.ori_leibo.nbafantasyscout.trend.FormClassifier.Form;
import java.util.List;
import org.junit.jupiter.api.Test;

class RosterRulesTest {

    private static final RosterRules RULES = new RosterRules(RosterRules.DEFAULT_ORDER);
    private static final double GAP = 0.5;

    private static int nextId = 1;

    private static PlayerForm form(String name, String status, Double value, Form form, CrowdSignal signal) {
        return new PlayerForm(new Player(nextId++, name, "LAL", "G", status, 3), 10, null, null, value, form, null,
                signal);
    }

    private static PlayerForm healthy(String name, double value, Form form) {
        return form(name, null, value, form, CrowdSignal.NONE);
    }

    // the best free agent is worth 0.8; an injured one worth more is never a replacement
    private static final PlayerForm WAIVER_GUARD = healthy("Waiver Guard", 0.8, Form.NEUTRAL);
    private static final FreeAgents FREE_AGENTS = new FreeAgents(List.of(
            healthy("Deep Bench", -1.0, Form.NEUTRAL),
            WAIVER_GUARD,
            form("Injured Free Agent", "Out", 3.0, Form.GOOD, CrowdSignal.NONE),
            form("No Games Yet", null, null, Form.NEUTRAL, CrowdSignal.NONE)), GAP);

    @Test
    void goodFormIsKeptAndAClearlyBetterFreeAgentReplacesAWeakPlayer() {
        assertEquals(new Recommendation.Keep("Form: HOT"), RULES.recommend(healthy("Rising", -0.5, Form.HOT), FREE_AGENTS));

        Recommendation weak = RULES.recommend(healthy("Weak", 0.1, Form.NEUTRAL), FREE_AGENTS);
        assertEquals(WAIVER_GUARD, assertInstanceOf(Recommendation.Drop.class, weak).replacement(),
                "0.8 - 0.1 = 0.7 clears the gap; the injured free agent is never suggested");
    }

    @Test
    void aSmallDifferenceIsNoiseSoThePlayerIsWatched() {
        // 0.8 - 0.5 = 0.3, under the gap of 0.5
        assertInstanceOf(Recommendation.Watch.class, RULES.recommend(healthy("Average", 0.5, Form.NEUTRAL), FREE_AGENTS));
        // no games this season: nothing to compare
        assertInstanceOf(Recommendation.Watch.class,
                RULES.recommend(form("Unknown", null, null, Form.NEUTRAL, CrowdSignal.NONE), FREE_AGENTS));
    }

    @Test
    void anInjuredStarIsHeldButAnInjuredWeakPlayerIsReplaced() {
        // injury runs before form: a cold star who is out is still held
        assertInstanceOf(Recommendation.Keep.class,
                RULES.recommend(form("Injured Star", "Out", 4.0, Form.COLD, CrowdSignal.NONE), FREE_AGENTS));

        Recommendation injuredWeak = RULES.recommend(form("Injured Weak", "Out", -0.2, Form.NEUTRAL, CrowdSignal.NONE),
                FREE_AGENTS);
        assertEquals(WAIVER_GUARD, assertInstanceOf(Recommendation.Drop.class, injuredWeak).replacement());

        // day-to-day is judged like a healthy player
        assertEquals(new Recommendation.Keep("Form: GOOD"),
                RULES.recommend(form("Sore Ankle", "Day-To-Day", 2.0, Form.GOOD, CrowdSignal.NONE), FREE_AGENTS));
    }

    @Test
    void crowdDropsAreJudgedAgainstTheStats() {
        // panic: the crowd drops a player whose stats are fine; the crowd rule runs before form, so its reason wins
        assertEquals(new Recommendation.Keep("Being dropped widely, but his stats are fine: hold"),
                RULES.recommend(form("Panic", null, 0.1, Form.HOT, CrowdSignal.PANIC_DROP), FREE_AGENTS));

        // confirmed decline: any better free agent will do, here 0.8 vs 0.6 (under the gap)
        Recommendation declining = RULES.recommend(form("Declining", null, 0.6, Form.COLD, CrowdSignal.CONFIRMED_DECLINE),
                FREE_AGENTS);
        assertEquals(WAIVER_GUARD, assertInstanceOf(Recommendation.Drop.class, declining).replacement());

        // confirmed decline but nobody better: watched, never dropped for a worse player
        assertInstanceOf(Recommendation.Watch.class,
                RULES.recommend(form("Declining Star", null, 2.0, Form.COLD, CrowdSignal.CONFIRMED_DECLINE), FREE_AGENTS));
    }
}
