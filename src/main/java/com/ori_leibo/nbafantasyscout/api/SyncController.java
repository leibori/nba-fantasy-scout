package com.ori_leibo.nbafantasyscout.api;

import com.ori_leibo.nbafantasyscout.api.dto.SyncJobResponse;
import com.ori_leibo.nbafantasyscout.sync.SyncJobs;
import io.swagger.v3.oas.annotations.Operation;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.NoSuchElementException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/sync")
public class SyncController {

    private final SyncJobs jobs;
    private final byte[] token;

    /** {@code token}: what callers must send in {@code X-Sync-Token}; empty = nobody may start a sync over HTTP. */
    public SyncController(SyncJobs jobs, @Value("${scout.sync.token:}") String token) {
        this.jobs = jobs;
        this.token = token.getBytes(StandardCharsets.UTF_8);
    }

    @Operation(summary = "Start the daily sync in the background (about 40 s); returns the job to poll. "
            + "While a sync runs, returns that one instead of starting another. Needs the X-Sync-Token header")
    @PostMapping("/daily")
    public ResponseEntity<SyncJobResponse> startDaily(@RequestHeader(value = "X-Sync-Token", required = false) String given) {
        // a sync costs ~525 ESPN calls, so on a public server only the owner may start one; no token set = refuse,
        // so forgetting to configure it never leaves the endpoint open. isEqual takes the same time for any guess
        if (token.length == 0 || given == null || !MessageDigest.isEqual(token, given.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "A valid X-Sync-Token header is required");
        }
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
