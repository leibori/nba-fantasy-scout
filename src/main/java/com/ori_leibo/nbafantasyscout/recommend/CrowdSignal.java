package com.ori_leibo.nbafantasyscout.recommend;

import com.ori_leibo.nbafantasyscout.trend.FormClassifier.Form;

/**
 * What the crowd (Sleeper adds/drops) does, read against what the model says (the form).
 * Evidence shown to the user, never part of a score.
 */
public enum CrowdSignal {
    /** Rising, and the crowd is adding him: act now. */
    CONFIRMED_BREAKOUT,
    /** The crowd is adding a player the model sees nothing in, or a declining one. */
    HYPE,
    /** Rising, and the crowd hasn't noticed yet. */
    EARLY_PICKUP,
    /** Declining, and the crowd is dropping him. */
    CONFIRMED_DECLINE,
    /** The crowd is dropping a player whose stats are fine: hold, or buy low. */
    PANIC_DROP,
    /** Declining, and the crowd hasn't noticed yet. */
    EARLY_WARNING,
    NONE;

    /** In the latest trending lists: added, dropped, or in neither. */
    public enum Crowd { ADDS, DROPS, QUIET }

    public static CrowdSignal of(Form form, Crowd crowd) {
        return switch (form) {
            case HOT, EARLY_HOT, RETURNING -> switch (crowd) {
                case ADDS -> CONFIRMED_BREAKOUT;
                case QUIET -> EARLY_PICKUP;
                case DROPS -> PANIC_DROP;
            };
            case COLD, EARLY_COLD -> switch (crowd) {
                case ADDS -> HYPE;
                case QUIET -> EARLY_WARNING;
                case DROPS -> CONFIRMED_DECLINE;
            };
            // a good player being added is sensible, not hype; being dropped is panic
            case GOOD -> crowd == Crowd.DROPS ? PANIC_DROP : NONE;
            // a bench player being dropped is not panic
            case NEUTRAL -> crowd == Crowd.ADDS ? HYPE : NONE;
        };
    }
}
