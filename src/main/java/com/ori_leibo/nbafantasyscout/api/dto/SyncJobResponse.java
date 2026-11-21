package com.ori_leibo.nbafantasyscout.api.dto;

import com.ori_leibo.nbafantasyscout.sync.SyncJobs;
import com.ori_leibo.nbafantasyscout.sync.SyncService;
import java.time.Instant;

/** A sync job's status; {@code result} once it is DONE, {@code error} if it FAILED. */
public record SyncJobResponse(String jobId, String status, Instant startedAt, Instant finishedAt, Result result,
                              String error) {

    public record Result(int players, int gamesChanged, int lastSeasonFetched, int failedPlayers, int snapshots,
                         long tookSeconds) {

        static Result from(SyncService.Result result) {
            return new Result(result.players(), result.gamesChanged(), result.lastSeasonFetched(),
                    result.failedPlayers(), result.snapshots(), result.took().toSeconds());
        }
    }

    public static SyncJobResponse from(SyncJobs.Job job) {
        return new SyncJobResponse(job.id(), job.status().name(), job.startedAt(), job.finishedAt(),
                job.result() == null ? null : Result.from(job.result()), job.error());
    }
}
