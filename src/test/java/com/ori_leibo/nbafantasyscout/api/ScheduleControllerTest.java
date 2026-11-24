package com.ori_leibo.nbafantasyscout.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ori_leibo.nbafantasyscout.recommend.WeekSchedule;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ScheduleController.class)
class ScheduleControllerTest {

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private WeekSchedule schedule;

    @Test
    void listsTeamsByGamesLeftAndSaysSoWhenTheScheduleIsUnavailable() throws Exception {
        when(schedule.thisWeek()).thenReturn(Optional.of(new WeekSchedule.Week(LocalDate.of(2026, 11, 23),
                LocalDate.of(2026, 11, 29), LocalDate.of(2026, 11, 24), Map.of("NY", new WeekSchedule.TeamWeek(3, 2),
                "BOS", new WeekSchedule.TeamWeek(4, 4), "MIA", new WeekSchedule.TeamWeek(2, 2)), Map.of(), Instant.EPOCH)));

        mvc.perform(get("/api/v1/schedule/week"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.start").value("2026-11-23"))
                .andExpect(jsonPath("$.teams[0].team").value("BOS"))
                .andExpect(jsonPath("$.teams[0].left").value(4))
                // a tie on games left is broken by name
                .andExpect(jsonPath("$.teams[1].team").value("MIA"))
                .andExpect(jsonPath("$.teams[2].team").value("NY"));

        when(schedule.thisWeek()).thenReturn(Optional.empty());
        mvc.perform(get("/api/v1/schedule/week")).andExpect(status().isServiceUnavailable());
    }
}
