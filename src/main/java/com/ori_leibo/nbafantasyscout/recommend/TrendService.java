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

    public TrendService(SeasonData seasonData, FormClassifier classifier, double percentageWeight) {
        this.seasonData = seasonData;
        this.classifier = classifier;
        this.percentageWeight = percentageWeight;
    }

    /**
     * One player's form. {@code trend} and {@code hotScore} are null when there is no trend yet;
     * {@code value} is null when the player has no games this season; {@code crowd} is his row in the latest
     * Sleeper trending lists, null when he is in neither.
     */
    public record PlayerForm(Player player, int gamesThisSeason, Trend trend, Double hotScore, Double value,
                             FormClassifier.Form form, TrendingSnapshot crowd, CrowdSignal crowdSignal) {
    }

    /** Every player on a roster, in no particular order; recomputed per call, only the season data is cached. */
    public List<PlayerForm> formOfAllPlayers(LeagueScoring scoring) {
        SeasonData.Snapshot data = seasonData.load();
        Optional<ValueScoring> valueScoring = data.league().isEmpty()
                ? Optional.empty()
                : Optional.of(valueScoring(scoring, data.league()));
        return data.players().stream()
                .map(season -> {
                    OptionalDouble value = season.line() == null || valueScoring.isEmpty()
                            ? OptionalDouble.empty()
                            : OptionalDouble.of(valueScoring.get().score(season.line()));
                    OptionalDouble hotScore = season.hotScore() == null
                            ? OptionalDouble.empty()
                            : OptionalDouble.of(season.hotScore());
                    FormClassifier.Form form = classifier.classify(Optional.ofNullable(season.trend()), hotScore, value,
                            season.history());
                    return new PlayerForm(season.player(), season.gamesThisSeason(), season.trend(), season.hotScore(),
                            value.isPresent() ? value.getAsDouble() : null,
                            form, season.crowd(), CrowdSignal.of(form, crowd(season.crowd())));
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
