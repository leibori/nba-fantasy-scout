package com.ori_leibo.nbafantasyscout.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ori_leibo.nbafantasyscout.sync.SyncJobs;
import com.ori_leibo.nbafantasyscout.sync.SyncService;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SyncController.class)
class SyncControllerTest {

    private static final Instant START = Instant.parse("2026-11-22T08:00:00Z");

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private SyncJobs jobs;

    @Test
    void startReturns202WithWhereToPoll() throws Exception {
        when(jobs.start()).thenReturn(new SyncJobs.Job("abc", SyncJobs.Status.RUNNING, START, null, null, null));

        mvc.perform(post("/api/v1/sync/daily"))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/api/v1/sync/jobs/abc"))
                .andExpect(jsonPath("$.jobId").value("abc"))
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    void aFinishedJobShowsWhatItSyncedAndAnUnknownOneIs404() throws Exception {
        when(jobs.find("abc")).thenReturn(Optional.of(new SyncJobs.Job("abc", SyncJobs.Status.DONE, START,
                START.plusSeconds(40), new SyncService.Result(528, 660, 12, 0, 50, Duration.ofSeconds(40)), null)));
        when(jobs.find("nope")).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/sync/jobs/abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.result.players").value(528))
                .andExpect(jsonPath("$.result.tookSeconds").value(40));
        mvc.perform(get("/api/v1/sync/jobs/nope"))
                .andExpect(status().isNotFound());
    }
}
