package com.ori_leibo.nbafantasyscout.roster;

import com.ori_leibo.nbafantasyscout.recommend.TrendService.PlayerForm;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The free agents a user could pick up right now: healthy, with a value this season, best first. The best of them is
 * the league's replacement level, the player anyone can add for free. {@code dropGap} is how much more value a
 * free agent needs before swapping is worth it; smaller differences are noise.
 */
public record FreeAgents(List<PlayerForm> available, double dropGap) {

    public FreeAgents {
        // an injured free agent is never a replacement, and one without games can't be compared
        available = available.stream()
                .filter(form -> form.value() != null && !RosterRules.isOut(form))
                .sorted(Comparator.comparing(PlayerForm::value).reversed())
                .toList();
    }

    public Optional<PlayerForm> best() {
        return available.stream().findFirst();
    }

    /** The best free agent if he beats {@code player} by at least the gap; empty for a player without a value. */
    public Optional<PlayerForm> clearlyBetterThan(PlayerForm player) {
        return best().filter(agent -> player.value() != null && agent.value() - player.value() >= dropGap);
    }

    /** The best free agent if he beats {@code player} at all. */
    public Optional<PlayerForm> betterThan(PlayerForm player) {
        return best().filter(agent -> player.value() != null && agent.value() > player.value());
    }
}
