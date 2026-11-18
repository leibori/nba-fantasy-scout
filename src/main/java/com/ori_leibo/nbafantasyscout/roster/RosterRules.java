package com.ori_leibo.nbafantasyscout.roster;

import com.ori_leibo.nbafantasyscout.recommend.CrowdSignal;
import com.ori_leibo.nbafantasyscout.recommend.TrendService.PlayerForm;
import com.ori_leibo.nbafantasyscout.trend.FormClassifier.Form;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Keep / watch / drop as a chain of responsibility: the rules run in order and the first one that decides wins;
 * a player no rule decides about is watched. The order matters: injury before form, so an injured star is held
 * whatever his trend says, and crowd evidence before the plain value comparison.
 */
public class RosterRules {

    private static final Set<Form> GOOD = EnumSet.of(Form.HOT, Form.EARLY_HOT, Form.RETURNING, Form.GOOD);

    public static final List<RosterRule> DEFAULT_ORDER = List.of(
            RosterRules::holdInjured, RosterRules::crowd, RosterRules::keepGood, RosterRules::replaceWeak);

    private final List<RosterRule> rules;

    public RosterRules(List<RosterRule> rules) {
        this.rules = List.copyOf(rules);
    }

    public Recommendation recommend(PlayerForm player, FreeAgents freeAgents) {
        return rules.stream()
                .map(rule -> rule.apply(player, freeAgents))
                .flatMap(Optional::stream)
                .findFirst()
                .orElseGet(() -> new Recommendation.Watch("No clear call yet"));
    }

    static boolean isOut(PlayerForm form) {
        return "Out".equalsIgnoreCase(form.player().status());
    }

    // ESPN gives a status, not how long he is out: hold him unless even a free agent is clearly better
    // (a star stays, a weak injured player falls through to replaceWeak)
    static Optional<Recommendation> holdInjured(PlayerForm player, FreeAgents freeAgents) {
        if (isOut(player) && freeAgents.clearlyBetterThan(player).isEmpty()) {
            return Optional.of(new Recommendation.Keep("Out, but no free agent is clearly better: hold until he's back"));
        }
        return Optional.empty();
    }

    // the crowd dropping him is evidence, judged against his stats: panic = hold, confirmed decline = swap
    static Optional<Recommendation> crowd(PlayerForm player, FreeAgents freeAgents) {
        if (player.crowdSignal() == CrowdSignal.PANIC_DROP) {
            return Optional.of(new Recommendation.Keep("Being dropped widely, but his stats are fine: hold"));
        }
        if (player.crowdSignal() == CrowdSignal.CONFIRMED_DECLINE) {
            // stats and crowd agree, so any better free agent is worth it, not only a clearly better one
            return freeAgents.betterThan(player).map(agent -> new Recommendation.Drop(agent,
                    "Declining and being dropped widely; " + agent.player().name() + " is worth more"));
        }
        return Optional.empty();
    }

    static Optional<Recommendation> keepGood(PlayerForm player, FreeAgents freeAgents) {
        return GOOD.contains(player.form())
                ? Optional.of(new Recommendation.Keep("Form: " + player.form()))
                : Optional.empty();
    }

    static Optional<Recommendation> replaceWeak(PlayerForm player, FreeAgents freeAgents) {
        return freeAgents.clearlyBetterThan(player).map(agent -> new Recommendation.Drop(agent,
                "%s is worth %.2f more".formatted(agent.player().name(), agent.value() - player.value())));
    }
}
