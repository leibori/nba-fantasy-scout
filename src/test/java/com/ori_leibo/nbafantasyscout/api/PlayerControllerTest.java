package com.ori_leibo.nbafantasyscout.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ori_leibo.nbafantasyscout.domain.Player;
import com.ori_leibo.nbafantasyscout.recommend.CrowdSignal;
import com.ori_leibo.nbafantasyscout.recommend.RecommendationService;
import com.ori_leibo.nbafantasyscout.recommend.TrendService.PlayerForm;
import com.ori_leibo.nbafantasyscout.trend.FormClassifier.Form;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PlayerController.class)
class PlayerControllerTest {

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private RecommendationService recommendations;

    @Test
    void rookieWithoutATrendHasNullTrendFields() throws Exception {
        when(recommendations.player(5041935)).thenReturn(Optional.of(new PlayerForm(
                new Player(5041935, "Cameron Boozer", "MEM", "F", null, 0), 4, null, null, 2.39, Form.EARLY_HOT,
                null, CrowdSignal.EARLY_PICKUP)));

        mvc.perform(get("/api/v1/players/5041935/trend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.form").value("EARLY_HOT"))
                .andExpect(jsonPath("$.hotScore").isEmpty())
                .andExpect(jsonPath("$.recent").isEmpty())
                .andExpect(jsonPath("$.crowd.signal").value("EARLY_PICKUP"))
                .andExpect(jsonPath("$.crowd.move").isEmpty());
    }

    @Test
    void unknownPlayerIsA404ProblemDetail() throws Exception {
        when(recommendations.player(1)).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/players/1/trend"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No rostered player with id 1"));
    }
}
