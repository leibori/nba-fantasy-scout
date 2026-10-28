package com.ori_leibo.nbafantasyscout.recommend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.ori_leibo.nbafantasyscout.domain.Player;
import com.ori_leibo.nbafantasyscout.domain.StatCategory;
import com.ori_leibo.nbafantasyscout.domain.StatLine;
import com.ori_leibo.nbafantasyscout.recommend.TrendService.PlayerForm;
import com.ori_leibo.nbafantasyscout.trend.FormClassifier.Form;
import com.ori_leibo.nbafantasyscout.trend.Trend;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock
    private TrendService trends;
    @InjectMocks
    private RecommendationService recommendations;

    private static StatLine blocks(double blocks) {
        return new StatLine(30, 5, 10, 1, 2, 3, 6, 3, 1, blocks, 2, 13);
    }

    private static PlayerForm form(long id, Form form, Double value, Trend trend) {
        return new PlayerForm(new Player(id, "Player " + id, "LAL", "C", null, 3), 4, trend,
                trend == null ? null : 0.2, value, form, null, CrowdSignal.NONE);
    }

    @Test
    void hotListHasOnlyRisingPlayersBestValueFirst() {
        when(trends.formOfAllPlayers()).thenReturn(List.of(
                form(1, Form.GOOD, 9.0, null),
                form(2, Form.EARLY_HOT, 2.0, null),
                form(3, Form.RETURNING, 5.0, null),
                form(4, Form.HOT, 3.0, null),
                form(5, Form.EARLY_COLD, 1.0, null)));

        assertEquals(List.of(3L, 4L, 2L), recommendations.hot(10).stream().map(f -> f.player().id()).toList());
        assertEquals(List.of(3L), recommendations.hot(1).stream().map(f -> f.player().id()).toList());
    }

    @Test
    void categoryRankingSkipsPlayersWithoutATrend() {
        when(trends.formOfAllPlayers()).thenReturn(List.of(
                form(1, Form.NEUTRAL, 0.0, new Trend(blocks(1.5), blocks(1.0), Trend.Baseline.THIS_SEASON)),
                form(2, Form.NEUTRAL, 0.0, new Trend(blocks(3.0), blocks(1.0), Trend.Baseline.THIS_SEASON)),
                form(3, Form.EARLY_HOT, 4.0, null)));

        List<RecommendationService.CategoryChange> changes = recommendations.byCategory(StatCategory.BLK, 10);

        assertEquals(List.of(2L, 1L), changes.stream().map(c -> c.form().player().id()).toList());
        assertEquals(2.0, changes.getFirst().change(), 1e-9);
    }

    @Test
    void findsOnePlayerById() {
        when(trends.formOfAllPlayers()).thenReturn(List.of(form(1, Form.GOOD, 3.0, null)));

        assertEquals(1L, recommendations.player(1).orElseThrow().player().id());
        assertTrue(recommendations.player(99).isEmpty());
    }
}
