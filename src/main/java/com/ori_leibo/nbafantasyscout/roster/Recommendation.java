package com.ori_leibo.nbafantasyscout.roster;

import com.ori_leibo.nbafantasyscout.recommend.TrendService.PlayerForm;

/** The advice for one player on the user's roster, with the reason shown to the user. */
public sealed interface Recommendation {

    String reason();

    record Keep(String reason) implements Recommendation {
    }

    record Watch(String reason) implements Recommendation {
    }

    /** {@code replacement}: the free agent to pick up instead. */
    record Drop(PlayerForm replacement, String reason) implements Recommendation {
    }
}
