package com.ori_leibo.nbafantasyscout.recommend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ori_leibo.nbafantasyscout.domain.LeagueScoring;
import com.ori_leibo.nbafantasyscout.domain.Player;
import com.ori_leibo.nbafantasyscout.domain.PointStat;
import com.ori_leibo.nbafantasyscout.domain.StatCategory;
import com.ori_leibo.nbafantasyscout.domain.StatLine;
import com.ori_leibo.nbafantasyscout.recommend.TrendService.PlayerForm;
import com.ori_leibo.nbafantasyscout.trend.FormClassifier.Form;
import com.ori_leibo.nbafantasyscout.trend.Trend;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock
    private TrendService trends;
    private RecommendationService recommendations;
    private static final LeagueScoring.Points POINTS = new LeagueScoring.Points(Map.of(PointStat.PTS, 1.0));

    @BeforeEach
    void setUp() {
        recommendations = new RecommendationService(trends, LeagueScoring.Format.CATEGORIES, POINTS);
    }

    private static StatLine blocks(double blocks) {
        return new StatLine(30, 5, 10, 1, 2, 3, 6, 3, 1, blocks, 2, 13);
    }

    private static PlayerForm form(long id, Form form, Double value, Trend trend) {
        return new PlayerForm(new Player(id, "Player " + id, "LAL", "C", null, 3), 4, trend,
                trend == null ? null : 0.2, value, form, null, CrowdSignal.NONE);
    }

    @Test
    void hotListHasOnlyRisingPlayersBestValueFirst() {
        when(trends.formOfAllPlayers(any())).thenReturn(List.of(
                form(1, Form.GOOD, 9.0, null),
                form(2, Form.EARLY_HOT, 2.0, null),
                form(3, Form.RETURNING, 5.0, null),
                form(4, Form.HOT, 3.0, null),
                form(5, Form.EARLY_COLD, 1.0, null),
                new PlayerForm(new Player(6, "Guard", "LAL", "G", null, 3), 4, null, null, 4.0, Form.HOT, null,
                        CrowdSignal.NONE)));

        assertEquals(List.of(3L, 6L, 4L, 2L), recommendations.hot(10, null, null).stream().map(f -> f.player().id()).toList());
        assertEquals(List.of(3L), recommendations.hot(1, null, null).stream().map(f -> f.player().id()).toList());
        // the position filter runs before the limit, so a limit still means that many guards
        assertEquals(List.of(6L), recommendations.hot(1, null, "G").stream().map(f -> f.player().id()).toList());
    }

    @Test
    void categoryRankingSkipsPlayersWithoutATrend() {
        when(trends.formOfAllPlayers(any())).thenReturn(List.of(
                form(1, Form.NEUTRAL, 0.0, new Trend(blocks(1.5), blocks(1.0), Trend.Baseline.THIS_SEASON)),
                form(2, Form.NEUTRAL, 0.0, new Trend(blocks(3.0), blocks(1.0), Trend.Baseline.THIS_SEASON)),
                form(3, Form.EARLY_HOT, 4.0, null)));

        List<RecommendationService.CategoryChange> changes = recommendations.byCategory(StatCategory.BLK, 10);

        assertEquals(List.of(2L, 1L), changes.stream().map(c -> c.form().player().id()).toList());
        assertEquals(2.0, changes.getFirst().change(), 1e-9);
    }

    @Test
    void findsOnePlayerById() {
        when(trends.formOfAllPlayers(any())).thenReturn(List.of(form(1, Form.GOOD, 3.0, null)));

        assertEquals(1L, recommendations.player(1).orElseThrow().player().id());
        assertTrue(recommendations.player(99).isEmpty());
    }

    @Test
    void hotUsesTheRequestedFormatAndOtherwiseTheDefault() {
        recommendations.hot(10, LeagueScoring.Format.POINTS, null);
        recommendations.hot(10, null, null);

        verify(trends).formOfAllPlayers(POINTS);
        verify(trends).formOfAllPlayers(new LeagueScoring.Categories());
    }
}
