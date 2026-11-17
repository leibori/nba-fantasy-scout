package com.ori_leibo.nbafantasyscout.roster;

import com.ori_leibo.nbafantasyscout.recommend.TrendService.PlayerForm;
import java.util.Optional;

/** One link of the roster chain: decides about a player, or returns empty to pass him to the next rule. */
@FunctionalInterface
public interface RosterRule {

    Optional<Recommendation> apply(PlayerForm player, FreeAgents freeAgents);
}
