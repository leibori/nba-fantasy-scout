package com.ori_leibo.nbafantasyscout.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ori_leibo.nbafantasyscout.domain.Player;
import com.ori_leibo.nbafantasyscout.domain.StatCategory;
import com.ori_leibo.nbafantasyscout.domain.StatLine;
import com.ori_leibo.nbafantasyscout.domain.TrendingSnapshot;
import com.ori_leibo.nbafantasyscout.recommend.CrowdSignal;
import com.ori_leibo.nbafantasyscout.recommend.RecommendationService;
import com.ori_leibo.nbafantasyscout.recommend.RecommendationService.CategoryChange;
import com.ori_leibo.nbafantasyscout.recommend.TrendService.PlayerForm;
import com.ori_leibo.nbafantasyscout.trend.FormClassifier.Form;
import com.ori_leibo.nbafantasyscout.trend.Trend;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// web layer only: the controller, the exception handler and JSON; the service is a mock
@WebMvcTest(RecommendationController.class)
class RecommendationControllerTest {

    private static final StatLine LINE = new StatLine(33.0, 6, 13, 1, 3, 4, 10.5, 4.8, 1, 0.5, 2, 14.8);
    private static final PlayerForm WALTER = new PlayerForm(
            new Player(4684272, "Ja'Kobe Walter", "TOR", "G", null, 1), 3,
            new Trend(LINE, LINE, Trend.Baseline.LAST_SEASON), 0.4131, 3.1712, Form.EARLY_HOT,
            new TrendingSnapshot(4684272, TrendingSnapshot.Type.ADD, 82600, Instant.parse("2026-10-28T06:00:00Z")),
            CrowdSignal.CONFIRMED_BREAKOUT);

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private RecommendationService recommendations;

    @Test
    void hotListReturnsFormDtos() throws Exception {
        when(recommendations.hot(10)).thenReturn(List.of(WALTER));

        mvc.perform(get("/api/v1/recommendations/hot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Ja'Kobe Walter"))
                .andExpect(jsonPath("$[0].form").value("EARLY_HOT"))
                .andExpect(jsonPath("$[0].value").value(3.171))
                .andExpect(jsonPath("$[0].baselineSource").value("LAST_SEASON"))
                .andExpect(jsonPath("$[0].provisional").value(true))
                .andExpect(jsonPath("$[0].recent.rebounds").value(10.5))
                .andExpect(jsonPath("$[0].crowd.signal").value("CONFIRMED_BREAKOUT"))
                .andExpect(jsonPath("$[0].crowd.count").value(82600));
    }

    @Test
    void categoryIsCaseInsensitive() throws Exception {
        when(recommendations.byCategory(StatCategory.BLK, 5)).thenReturn(List.of(new CategoryChange(WALTER, 1.25)));

        mvc.perform(get("/api/v1/recommendations/blk").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].category").value("BLK"))
                .andExpect(jsonPath("$[0].change").value(1.25))
                .andExpect(jsonPath("$[0].player.name").value("Ja'Kobe Walter"));
    }

    @Test
    void badInputIsA400ProblemDetail() throws Exception {
        mvc.perform(get("/api/v1/recommendations/dunks"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.startsWith("Unknown category \"dunks\"")));

        mvc.perform(get("/api/v1/recommendations/hot").param("limit", "0"))
                .andExpect(status().isBadRequest());
    }
}
