package com.ori_leibo.nbafantasyscout.api;

import com.ori_leibo.nbafantasyscout.api.dto.SyncJobResponse;
import com.ori_leibo.nbafantasyscout.sync.SyncJobs;
import io.swagger.v3.oas.annotations.Operation;
import java.net.URI;
import java.util.NoSuchElementException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sync")
public class SyncController {

    private final SyncJobs jobs;

    public SyncController(SyncJobs jobs) {
        this.jobs = jobs;
    }

    @Operation(summary = "Start the daily sync in the background (about 40 s); returns the job to poll. "
            + "While a sync runs, returns that one instead of starting another")
    @PostMapping("/daily")
    public ResponseEntity<SyncJobResponse> startDaily() {
        SyncJobs.Job job = jobs.start();
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/sync/jobs/" + job.id()))
                .body(SyncJobResponse.from(job));
    }

    @Operation(summary = "A sync job's status: RUNNING, DONE (with what it synced) or FAILED (with the error); "
            + "the last 20 jobs are kept")
    @GetMapping("/jobs/{jobId}")
    public SyncJobResponse job(@PathVariable String jobId) {
        return jobs.find(jobId).map(SyncJobResponse::from)
                .orElseThrow(() -> new NoSuchElementException("No sync job " + jobId));
    }
}
