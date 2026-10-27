package com.ori_leibo.nbafantasyscout.recommend;

import com.ori_leibo.nbafantasyscout.domain.GameLog;
import com.ori_leibo.nbafantasyscout.domain.Player;
import com.ori_leibo.nbafantasyscout.domain.StatLine;
import com.ori_leibo.nbafantasyscout.persistence.GameLogEntity;
import com.ori_leibo.nbafantasyscout.persistence.GameLogRepository;
import com.ori_leibo.nbafantasyscout.persistence.PlayerEntity;
import com.ori_leibo.nbafantasyscout.persistence.PlayerRepository;
import com.ori_leibo.nbafantasyscout.trend.FormClassifier;
import com.ori_leibo.nbafantasyscout.trend.ScoringStrategy;
import com.ori_leibo.nbafantasyscout.trend.Trend;
import com.ori_leibo.nbafantasyscout.trend.TrendCalculator;
import com.ori_leibo.nbafantasyscout.trend.ValueScoring;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Loads stored players and games and runs the pure trend logic on them: trend, hot score, value and form. */
public class TrendService {

    private static final Logger log = LoggerFactory.getLogger(TrendService.class);

    private final PlayerRepository players;
    private final GameLogRepository gameLogs;
    private final TrendCalculator calculator;
    private final ScoringStrategy hotScoring;
    private final FormClassifier classifier;
    private final int season;
    private final double leagueMinMinutes;
    private final double percentageWeight;

    public TrendService(PlayerRepository players, GameLogRepository gameLogs, TrendCalculator calculator,
                        ScoringStrategy hotScoring, FormClassifier classifier, int season,
                        double leagueMinMinutes, double percentageWeight) {
        this.players = players;
        this.gameLogs = gameLogs;
        this.calculator = calculator;
        this.hotScoring = hotScoring;
        this.classifier = classifier;
        this.season = season;
        this.leagueMinMinutes = leagueMinMinutes;
        this.percentageWeight = percentageWeight;
    }

    /**
     * One player's form. {@code trend} and {@code hotScore} are null when there is no trend yet;
     * {@code value} is null when the player has no games this season.
     */
    public record PlayerForm(Player player, int gamesThisSeason, Trend trend, Double hotScore, Double value,
                             FormClassifier.Form form) {
    }

    /** Every player on a roster, in no particular order. */
    public List<PlayerForm> formOfAllPlayers() {
        long start = System.nanoTime();
        List<Player> roster = players.findAll().stream()
                .map(PlayerEntity::toPlayer)
                .filter(player -> player.team() != null)
                .toList();
        // one query for all games (~25k small rows) instead of one per player
        Map<Long, List<GameLog>> gamesByPlayer = gameLogs.findAll().stream()
                .collect(Collectors.groupingBy(GameLogEntity::playerId,
                        Collectors.mapping(GameLogEntity::toGameLog, Collectors.toList())));

        Map<Long, List<GameLog>> thisSeason = new HashMap<>();
        Map<Long, List<GameLog>> lastSeason = new HashMap<>();
        Map<Long, StatLine> thisSeasonLines = new HashMap<>();
        for (Player player : roster) {
            List<GameLog> games = gamesByPlayer.getOrDefault(player.id(), List.of());
            List<GameLog> current = games.stream().filter(game -> game.season() == season).toList();
            thisSeason.put(player.id(), current);
            lastSeason.put(player.id(), games.stream().filter(game -> game.season() == season - 1).toList());
            if (!current.isEmpty()) {
                thisSeasonLines.put(player.id(), StatLine.average(current));
            }
        }

        List<StatLine> league = thisSeasonLines.values().stream()
                .filter(line -> line.minutes() >= leagueMinMinutes)
                .toList();
        Optional<ValueScoring> valueScoring = league.isEmpty()
                ? Optional.empty()
                : Optional.of(new ValueScoring(league, percentageWeight));

        List<PlayerForm> forms = new ArrayList<>();
        for (Player player : roster) {
            List<GameLog> last = lastSeason.get(player.id());
            Optional<Trend> trend = calculator.calculate(thisSeason.get(player.id()), last);
            StatLine line = thisSeasonLines.get(player.id());
            OptionalDouble value = line == null || valueScoring.isEmpty()
                    ? OptionalDouble.empty()
                    : OptionalDouble.of(valueScoring.get().score(line));
            forms.add(new PlayerForm(player, thisSeason.get(player.id()).size(), trend.orElse(null),
                    trend.map(hotScoring::score).orElse(null),
                    value.isPresent() ? value.getAsDouble() : null,
                    classifier.classify(trend, value, history(player, last))));
        }
        log.info("Computed form of {} players against a value league of {} in {} ms",
                forms.size(), league.size(), (System.nanoTime() - start) / 1_000_000);
        return forms;
    }

    // no games last season: a rookie by ESPN's experience count (0), otherwise back from a lost season
    private static FormClassifier.History history(Player player, List<GameLog> lastSeason) {
        if (!lastSeason.isEmpty()) {
            return FormClassifier.History.PLAYED_LAST_SEASON;
        }
        return Integer.valueOf(0).equals(player.experienceYears())
                ? FormClassifier.History.ROOKIE
                : FormClassifier.History.RETURNING;
    }
}
