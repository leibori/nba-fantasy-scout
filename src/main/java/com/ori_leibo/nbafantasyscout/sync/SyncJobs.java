package com.ori_leibo.nbafantasyscout.sync;

import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs the daily sync in the background and keeps its status, so a caller gets an id at once instead of waiting
 * the ~40 s a sync takes. One sync at a time: starting while one runs returns the running job.
 * Jobs live in memory; after a restart only the synced data remains, which is what matters.
 */
public class SyncJobs {

    private static final Logger log = LoggerFactory.getLogger(SyncJobs.class);
    private static final int KEPT_JOBS = 20;

    public enum Status { RUNNING, DONE, FAILED }

    /** {@code result} only when DONE, {@code error} only when FAILED. */
    public record Job(String id, Status status, Instant startedAt, Instant finishedAt, SyncService.Result result,
                      String error) {
    }

    private final Function<String, SyncService.Result> sync;
    private final Executor executor;
    private final Clock clock;
    private final AtomicReference<Job> running = new AtomicReference<>();
    // oldest first, the oldest dropped past KEPT_JOBS
    private final Map<String, Job> jobs = Collections.synchronizedMap(new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Job> eldest) {
            return size() > KEPT_JOBS;
        }
    });

    /** {@code sync} runs one sync and logs under the id it is given: the job id, so a job's logs can be found by it. */
    public SyncJobs(Function<String, SyncService.Result> sync, Executor executor, Clock clock) {
        this.sync = sync;
        this.executor = executor;
        this.clock = clock;
    }

    /** The new job, or the one already running. */
    public Job start() {
        Job job = new Job("sync-" + UUID.randomUUID().toString().substring(0, 8), Status.RUNNING, clock.instant(),
                null, null, null);
        while (true) {
            Job current = running.get();
            if (current != null) {
                return current;
            }
            // only one caller wins; a loser sees the winner's job on the next pass
            if (running.compareAndSet(null, job)) {
                break;
            }
        }
        jobs.put(job.id(), job);
        try {
            executor.execute(() -> run(job));
        } catch (RuntimeException e) {
            // never ran, so nothing will clear it: free the slot or no sync could start again
            jobs.put(job.id(), finished(job, Status.FAILED, null, "Could not start: " + e.getMessage()));
            running.set(null);
            throw e;
        }
        log.info("Sync job {} started", job.id());
        return job;
    }

    public Optional<Job> find(String id) {
        return Optional.ofNullable(jobs.get(id));
    }

    private void run(Job job) {
        try {
            jobs.put(job.id(), finished(job, Status.DONE, sync.apply(job.id()), null));
            log.info("Sync job {} done", job.id());
        } catch (RuntimeException e) {
            log.error("Sync job {} failed", job.id(), e);
            jobs.put(job.id(), finished(job, Status.FAILED, null, e.getMessage()));
        } finally {
            // after the final status is stored, so a caller never sees a free slot with a job still RUNNING
            running.set(null);
        }
    }

    private Job finished(Job job, Status status, SyncService.Result result, String error) {
        return new Job(job.id(), status, job.startedAt(), clock.instant(), result, error);
    }
}
