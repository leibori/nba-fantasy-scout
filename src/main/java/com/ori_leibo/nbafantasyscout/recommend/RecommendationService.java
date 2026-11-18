package com.ori_leibo.nbafantasyscout.recommend;

import com.ori_leibo.nbafantasyscout.domain.LeagueScoring;
import com.ori_leibo.nbafantasyscout.domain.StatCategory;
import com.ori_leibo.nbafantasyscout.recommend.TrendService.PlayerForm;
import com.ori_leibo.nbafantasyscout.trend.CategoryScoring;
import com.ori_leibo.nbafantasyscout.trend.FormClassifier.Form;
import com.ori_leibo.nbafantasyscout.trend.ScoringStrategy;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** The read side of the API: rankings and single players, built from {@link TrendService}'s forms. */
public class RecommendationService {

    public static final Set<Form> RISING = EnumSet.of(Form.HOT, Form.EARLY_HOT, Form.RETURNING);

    private final TrendService trends;
    private final LeagueScoring.Format defaultFormat;
    private final LeagueScoring.Points points;

    /** {@code points}: the point values used whenever the points format is asked for. */
    public RecommendationService(TrendService trends, LeagueScoring.Format defaultFormat, LeagueScoring.Points points) {
        this.trends = trends;
        this.defaultFormat = defaultFormat;
        this.points = points;
    }

    public LeagueScoring defaultScoring() {
        return scoring(defaultFormat);
    }

    /** One player's change in one category vs. his baseline; positive = better (fewer turnovers counts as better). */
    public record CategoryChange(PlayerForm form, double change) {
    }

    /**
     * Rising players (hot, early hot, returning), best value first. Ranked by value, not hot score:
     * a rookie has no baseline and so no hot score, but every rising player has a value.
     */
    public List<PlayerForm> hot(int limit) {
        return hot(limit, defaultFormat);
    }

    public List<PlayerForm> hot(int limit, LeagueScoring.Format format) {
        return trends.formOfAllPlayers(scoring(format)).stream()
                .filter(form -> RISING.contains(form.form()))
                .sorted(Comparator.comparing(PlayerForm::value, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .toList();
    }

    /** Biggest improvement in one category; players without a trend (no baseline yet) are left out. */
    public List<CategoryChange> byCategory(StatCategory category, int limit) {
        ScoringStrategy scoring = new CategoryScoring(category);
        return trends.formOfAllPlayers(defaultScoring()).stream()
                .filter(form -> form.trend() != null)
                .map(form -> new CategoryChange(form, scoring.score(form.trend())))
                .sorted(Comparator.comparingDouble(CategoryChange::change).reversed())
                .limit(limit)
                .toList();
    }

    /** Empty when no rostered player has this ESPN id. */
    public Optional<PlayerForm> player(long playerId) {
        return trends.formOfAllPlayers(defaultScoring()).stream()
                .filter(form -> form.player().id() == playerId)
                .findFirst();
    }

    private LeagueScoring scoring(LeagueScoring.Format format) {
        return switch (format) {
            case CATEGORIES -> new LeagueScoring.Categories();
            case POINTS -> points;
        };
    }
}
