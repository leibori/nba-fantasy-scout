package com.ori_leibo.nbafantasyscout.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ori_leibo.nbafantasyscout.domain.LeagueScoring;
import com.ori_leibo.nbafantasyscout.domain.Player;
import com.ori_leibo.nbafantasyscout.domain.PointStat;
import com.ori_leibo.nbafantasyscout.recommend.CrowdSignal;
import com.ori_leibo.nbafantasyscout.recommend.RecommendationService;
import com.ori_leibo.nbafantasyscout.recommend.TrendService.PlayerForm;
import com.ori_leibo.nbafantasyscout.roster.Recommendation;
import com.ori_leibo.nbafantasyscout.roster.RosterService;
import com.ori_leibo.nbafantasyscout.roster.RosterService.PlayerAdvice;
import com.ori_leibo.nbafantasyscout.roster.RosterService.RosterAdvice;
import com.ori_leibo.nbafantasyscout.trend.FormClassifier.Form;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// web layer only: request binding, validation and the JSON shape; the roster logic is mocked
@WebMvcTest(RosterController.class)
class RosterControllerTest {

    private static final LeagueScoring POINTS = new LeagueScoring.Points(Map.of(PointStat.PTS, 1.0));
    private static final PlayerForm EDEY = form(3, "Zach Edey", -0.2);
    private static final PlayerForm WAIVER_GUARD = form(4, "Waiver Guard", 0.9);

    private static PlayerForm form(long id, String name, double value) {
        return new PlayerForm(new Player(id, name, "MEM", "C", null, 2), 10, null, null, value, Form.NEUTRAL, null,
                CrowdSignal.NONE);
    }

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private RosterService rosterService;
    @MockitoBean
    private RecommendationService recommendations;

    @Test
    void returnsAdvicePerNameWithTheReplacementOnADrop() throws Exception {
        when(recommendations.defaultScoring()).thenReturn(POINTS);
        when(rosterService.advise(List.of("zach edey"), List.of(), POINTS)).thenReturn(new RosterAdvice(
                List.of(new PlayerAdvice("zach edey", EDEY, false,
                        new Recommendation.Drop(WAIVER_GUARD, "Waiver Guard is worth 1.10 more"))),
                List.of(WAIVER_GUARD), List.of()));

        // no freeAgents field: optional, sent to the service as an empty list
        mvc.perform(post("/api/v1/roster").contentType(MediaType.APPLICATION_JSON).content("""
                        {"myRoster": ["zach edey"]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roster[0].name").value("zach edey"))
                .andExpect(jsonPath("$.roster[0].advice").value("DROP"))
                .andExpect(jsonPath("$.roster[0].reason").value("Waiver Guard is worth 1.10 more"))
                .andExpect(jsonPath("$.roster[0].player.name").value("Zach Edey"))
                .andExpect(jsonPath("$.roster[0].replacement.value").value(0.9))
                .andExpect(jsonPath("$.pickUpNow[0].name").value("Waiver Guard"))
                .andExpect(jsonPath("$.unmatched").isEmpty());
    }

    @Test
    void aMissingOrEmptyRosterIsA400() throws Exception {
        mvc.perform(post("/api/v1/roster").contentType(MediaType.APPLICATION_JSON).content("""
                        {"myRoster": []}"""))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/roster").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        verify(rosterService, never()).advise(anyList(), anyList(), any());
    }
}
