package com.ori_leibo.nbafantasyscout.roster;

import com.ori_leibo.nbafantasyscout.domain.LeagueScoring;
import com.ori_leibo.nbafantasyscout.domain.Player;
import com.ori_leibo.nbafantasyscout.recommend.RecommendationService;
import com.ori_leibo.nbafantasyscout.recommend.TrendService;
import com.ori_leibo.nbafantasyscout.recommend.TrendService.PlayerForm;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Advice for a user's roster given as names: each player matched to an NBA player, judged by the roster rules
 * against the free agents, plus the free agents worth picking up now.
 */
public class RosterService {

    private final TrendService trends;
    private final PlayerMatcher matcher;
    private final RosterRules rules;
    private final double categoriesDropGap;
    private final double pointsDropGap;
    private final int pickUpNowSize;

    /** The drop gaps are per format: the formats score on different scales. */
    public RosterService(TrendService trends, PlayerMatcher matcher, RosterRules rules, double categoriesDropGap,
                         double pointsDropGap, int pickUpNowSize) {
        this.trends = trends;
        this.matcher = matcher;
        this.rules = rules;
        this.categoriesDropGap = categoriesDropGap;
        this.pointsDropGap = pointsDropGap;
        this.pickUpNowSize = pickUpNowSize;
    }

    /** {@code name} as the user typed it; {@code unsure}: the match was weak, so it may be the wrong player. */
    public record PlayerAdvice(String name, PlayerForm form, boolean unsure, Recommendation recommendation) {
    }

    /** {@code unmatched}: names that matched no player, and free agents whose match was too weak to suggest. */
    public record RosterAdvice(List<PlayerAdvice> roster, List<PlayerForm> pickUpNow, List<String> unmatched) {
    }

    /**
     * {@code freeAgentNames} empty = every NBA player not on the roster counts as available. {@code positions}: the
     * league's positions per name; a name without them uses the ESPN position (G/F/C). {@code samePosition}: a drop's
     * replacement must share a position with the dropped player.
     */
    public RosterAdvice advise(List<String> rosterNames, List<String> freeAgentNames, Map<String, Set<String>> positions,
                               boolean samePosition, LeagueScoring scoring) {
        List<PlayerForm> forms = trends.formOfAllPlayers(scoring);
        Map<Long, PlayerForm> formById = forms.stream().collect(Collectors.toMap(form -> form.player().id(), Function.identity()));
        List<Player> players = forms.stream().map(PlayerForm::player).toList();
        List<String> unmatched = new ArrayList<>();
        Map<Long, Set<String>> positionsById = new HashMap<>();

        Map<String, PlayerMatcher.Match> rosterMatches = new HashMap<>();
        for (String name : rosterNames) {
            matcher.match(name, players).ifPresentOrElse(match -> {
                rosterMatches.put(name, match);
                positionsById.put(match.player().id(), positions.getOrDefault(name, espnPosition(match.player())));
            }, () -> unmatched.add(name));
        }
        Set<Long> onRoster = new HashSet<>();
        rosterMatches.values().forEach(match -> onRoster.add(match.player().id()));

        List<PlayerForm> freeAgentForms = freeAgentNames.isEmpty()
                ? forms.stream().filter(form -> !onRoster.contains(form.player().id())).toList()
                : sureMatches(freeAgentNames, players, formById, unmatched, positions, positionsById);
        double dropGap = switch (scoring) {
            case LeagueScoring.Categories categories -> categoriesDropGap;
            case LeagueScoring.Points points -> pointsDropGap;
        };

        Function<PlayerForm, Set<String>> positionOf = form ->
                positionsById.getOrDefault(form.player().id(), espnPosition(form.player()));
        Map<String, Recommendation> recommendations = recommend(rosterMatches, formById, freeAgentForms, dropGap,
                samePosition ? positionOf : null);
        List<PlayerAdvice> roster = rosterNames.stream()
                .filter(rosterMatches::containsKey)
                .map(name -> {
                    PlayerMatcher.Match match = rosterMatches.get(name);
                    return new PlayerAdvice(name, formById.get(match.player().id()), match.unsure(), recommendations.get(name));
                })
                .toList();
        return new RosterAdvice(roster, pickUpNow(new FreeAgents(freeAgentForms, dropGap)), List.copyOf(unmatched));
    }

    // weakest first, so the weakest player gets the best free agent; a suggested free agent leaves the pool,
    // so two players are never told to pick up the same one. positionOf null = any position may replace anyone
    private Map<String, Recommendation> recommend(Map<String, PlayerMatcher.Match> rosterMatches,
                                                  Map<Long, PlayerForm> formById, List<PlayerForm> freeAgentForms,
                                                  double dropGap, Function<PlayerForm, Set<String>> positionOf) {
        List<PlayerForm> pool = new ArrayList<>(freeAgentForms);
        Map<String, Recommendation> recommendations = new HashMap<>();
        rosterMatches.entrySet().stream()
                .sorted(Comparator.comparing(entry -> formById.get(entry.getValue().player().id()).value(),
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(entry -> {
                    PlayerForm player = formById.get(entry.getValue().player().id());
                    List<PlayerForm> candidates = positionOf == null ? pool : pool.stream()
                            .filter(agent -> !Collections.disjoint(positionOf.apply(agent), positionOf.apply(player)))
                            .toList();
                    Recommendation recommendation = rules.recommend(player, new FreeAgents(candidates, dropGap));
                    if (recommendation instanceof Recommendation.Drop drop) {
                        pool.remove(drop.replacement());
                    }
                    recommendations.put(entry.getKey(), recommendation);
                });
        return recommendations;
    }

    // a weak free agent match could be the wrong player, and suggesting him would be worse than leaving him out
    private List<PlayerForm> sureMatches(List<String> names, List<Player> players, Map<Long, PlayerForm> formById,
                                         List<String> unmatched, Map<String, Set<String>> positions,
                                         Map<Long, Set<String>> positionsById) {
        List<PlayerForm> sure = new ArrayList<>();
        for (String name : names) {
            Optional<PlayerMatcher.Match> match = matcher.match(name, players);
            if (match.isPresent() && !match.get().unsure()) {
                sure.add(formById.get(match.get().player().id()));
                positionsById.put(match.get().player().id(), positions.getOrDefault(name, espnPosition(match.get().player())));
            } else {
                unmatched.add(name);
            }
        }
        return sure;
    }

    private static Set<String> espnPosition(Player player) {
        return player.position() == null ? Set.of() : Set.of(player.position());
    }

    // rising free agents first (the reason to act now), then the best value
    private List<PlayerForm> pickUpNow(FreeAgents freeAgents) {
        return freeAgents.available().stream()
                .sorted(Comparator.comparing((PlayerForm form) -> !RecommendationService.RISING.contains(form.form()))
                        .thenComparing(PlayerForm::value, Comparator.reverseOrder()))
                .limit(pickUpNowSize)
                .toList();
    }
}
