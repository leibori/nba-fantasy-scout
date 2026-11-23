package com.ori_leibo.nbafantasyscout.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ori_leibo.nbafantasyscout.client.YahooMapper;
import com.ori_leibo.nbafantasyscout.roster.YahooLeagueService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(LeagueController.class)
class LeagueControllerTest {

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private YahooLeagueService yahoo;

    @Test
    void listsTheTeamsWithTheirLogosButNotTheirYahooKeys() throws Exception {
        when(yahoo.teams(246889)).thenReturn(List.of(
                new YahooMapper.Team("478.l.246889.t.1", "Ramat-Gan Eshet Chayil", "https://s.yimg.com/logo.png"),
                new YahooMapper.Team("478.l.246889.t.2", "No Logo", null)));

        mvc.perform(get("/api/v1/leagues/246889/teams"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Ramat-Gan Eshet Chayil"))
                .andExpect(jsonPath("$[0].logoUrl").value("https://s.yimg.com/logo.png"))
                .andExpect(jsonPath("$[0].key").doesNotExist())
                .andExpect(jsonPath("$[1].logoUrl").isEmpty());
    }

    @Test
    void aLeagueFromAnotherSeasonIsA400WithTheReason() throws Exception {
        when(yahoo.teams(1)).thenThrow(new IllegalArgumentException("Yahoo league 1 is from the 2025-2026 season"));

        mvc.perform(get("/api/v1/leagues/1/teams"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Yahoo league 1 is from the 2025-2026 season"));
    }
}
