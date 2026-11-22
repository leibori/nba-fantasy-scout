package com.ori_leibo.nbafantasyscout.recommend;

import com.ori_leibo.nbafantasyscout.domain.LeagueScoring;
import com.ori_leibo.nbafantasyscout.domain.Player;
import com.ori_leibo.nbafantasyscout.domain.StatLine;
import com.ori_leibo.nbafantasyscout.domain.TrendingSnapshot;
import com.ori_leibo.nbafantasyscout.trend.CategoryValueScoring;
import com.ori_leibo.nbafantasyscout.trend.FormClassifier;
import com.ori_leibo.nbafantasyscout.trend.PointsValueScoring;
import com.ori_leibo.nbafantasyscout.trend.Trend;
import com.ori_leibo.nbafantasyscout.trend.ValueScoring;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Every player's form in one league format: the cached {@link SeasonData} plus what depends on the format,
 * value (9-cat z-scores or fantasy points), the form built on it, and the crowd signal next to it.
 */
public class TrendService {

    private final SeasonData seasonData;
    private final FormClassifier classifier;
    private final double percentageWeight;
    private final double categoriesHighValue;
    private final double pointsHighValue;
    private final int inactiveAfterMissedGames;

    /** The high values are each format's "good right now" threshold: the formats score on different scales. */
    /** {@code inactiveAfterMissedGames}: a player who missed this many of his team's games in a row is inactive. */
    public TrendService(SeasonData seasonData, FormClassifier classifier, double percentageWeight,
                        double categoriesHighValue, double pointsHighValue, int inactiveAfterMissedGames) {
        this.seasonData = seasonData;
        this.classifier = classifier;
        this.percentageWeight = percentageWeight;
        this.categoriesHighValue = categoriesHighValue;
        this.pointsHighValue = pointsHighValue;
        this.inactiveAfterMissedGames = inactiveAfterMissedGames;
    }

    /**
     * One player's form. {@code trend} and {@code hotScore} are null when there is no trend yet;
     * {@code value} is null when the player has no games this season; {@code crowd} is his row in the latest
     * Sleeper trending lists, null when he is in neither.
     */
    /** {@code lastGame}: null with no game this season; {@code teamGamesMissed}: his team's games since then. */
    public record PlayerForm(Player player, int gamesThisSeason, Trend trend, Double hotScore, Double value,
                             FormClassifier.Form form, TrendingSnapshot crowd, CrowdSignal crowdSignal,
                             LocalDate lastGame, int teamGamesMissed) {

        /** A player whose last game and missed games don't matter to the caller. */
        public PlayerForm(Player player, int gamesThisSeason, Trend trend, Double hotScore, Double value,
                          FormClassifier.Form form, TrendingSnapshot crowd, CrowdSignal crowdSignal) {
            this(player, gamesThisSeason, trend, hotScore, value, form, crowd, crowdSignal, null, 0);
        }
    }

    /** Every player on a roster, in no particular order; recomputed per call, only the season data is cached. */
    public List<PlayerForm> formOfAllPlayers(LeagueScoring scoring) {
        SeasonData.Snapshot data = seasonData.load();
        Optional<ValueScoring> valueScoring = data.league().isEmpty()
                ? Optional.empty()
                : Optional.of(valueScoring(scoring, data.league()));
        double highValue = switch (scoring) {
            case LeagueScoring.Categories categories -> categoriesHighValue;
            case LeagueScoring.Points points -> pointsHighValue;
        };
        return data.players().stream()
                .map(season -> {
                    OptionalDouble value = season.line() == null || valueScoring.isEmpty()
                            ? OptionalDouble.empty()
                            : OptionalDouble.of(valueScoring.get().score(season.line()));
                    OptionalDouble hotScore = season.hotScore() == null
                            ? OptionalDouble.empty()
                            : OptionalDouble.of(season.hotScore());
                    boolean high = value.isPresent() && value.getAsDouble() >= highValue;
                    // his last games are old news once his team has moved on without him
                    boolean inactive = season.lastGame() != null && season.teamGamesMissed() >= inactiveAfterMissedGames;
                    FormClassifier.Form form = inactive
                            ? FormClassifier.Form.INACTIVE
                            : classifier.classify(Optional.ofNullable(season.trend()), hotScore, high, season.history());
                    return new PlayerForm(season.player(), season.gamesThisSeason(), season.trend(), season.hotScore(),
                            value.isPresent() ? value.getAsDouble() : null,
                            form, season.crowd(), CrowdSignal.of(form, crowd(season.crowd())),
                            season.lastGame(), season.teamGamesMissed());
                })
                .toList();
    }

    private ValueScoring valueScoring(LeagueScoring scoring, List<StatLine> league) {
        return switch (scoring) {
            case LeagueScoring.Categories categories -> new CategoryValueScoring(league, percentageWeight);
            case LeagueScoring.Points points -> new PointsValueScoring(league, points);
        };
    }

    private static CrowdSignal.Crowd crowd(TrendingSnapshot snapshot) {
        if (snapshot == null) {
            return CrowdSignal.Crowd.QUIET;
        }
        return snapshot.type() == TrendingSnapshot.Type.ADD ? CrowdSignal.Crowd.ADDS : CrowdSignal.Crowd.DROPS;
    }
}
