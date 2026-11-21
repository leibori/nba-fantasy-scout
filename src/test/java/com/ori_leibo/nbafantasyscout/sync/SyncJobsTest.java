package com.ori_leibo.nbafantasyscout.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

// the executor only collects tasks, so each test decides when the "background" sync runs
class SyncJobsTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-22T08:00:00Z"), ZoneOffset.UTC);
    private static final SyncService.Result RESULT = new SyncService.Result(528, 660, 12, 0, 50, Duration.ofSeconds(40));

    private final List<Runnable> tasks = new ArrayList<>();

    @Test
    void aStartWhileRunningGetsTheSameJobAndAFinishedJobFreesTheSlot() {
        SyncJobs jobs = new SyncJobs(() -> RESULT, tasks::add, CLOCK);

        SyncJobs.Job first = jobs.start();
        assertEquals(SyncJobs.Status.RUNNING, first.status());
        assertEquals(first, jobs.start());
        assertEquals(1, tasks.size());

        tasks.getFirst().run();
        SyncJobs.Job done = jobs.find(first.id()).orElseThrow();
        assertEquals(SyncJobs.Status.DONE, done.status());
        assertEquals(RESULT, done.result());
        assertNotNull(done.finishedAt());

        assertNotEquals(first.id(), jobs.start().id());
    }

    @Test
    void aSyncThatFailsOrCannotStartFreesTheSlot() {
        SyncJobs jobs = new SyncJobs(() -> {
            throw new IllegalStateException("ESPN returned no players");
        }, tasks::add, CLOCK);

        SyncJobs.Job job = jobs.start();
        tasks.getFirst().run();
        SyncJobs.Job failed = jobs.find(job.id()).orElseThrow();
        assertEquals(SyncJobs.Status.FAILED, failed.status());
        assertEquals("ESPN returned no players", failed.error());
        assertNotEquals(job.id(), jobs.start().id());

        // a full executor: the job never runs, so start has to free the slot itself
        boolean[] reject = {true};
        SyncJobs rejecting = new SyncJobs(() -> RESULT, task -> {
            if (reject[0]) {
                throw new RejectedExecutionException("busy");
            }
        }, CLOCK);
        assertThrows(RejectedExecutionException.class, rejecting::start);
        reject[0] = false;
        assertEquals(SyncJobs.Status.RUNNING, rejecting.start().status());
    }

    @Test
    void manyCallersAtOnceStartOneSync() throws Exception {
        SyncJobs jobs = new SyncJobs(() -> RESULT, tasks::add, CLOCK);
        int callers = 16;
        Set<String> ids = ConcurrentHashMap.newKeySet();
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        for (int i = 0; i < callers; i++) {
            pool.execute(() -> {
                try {
                    go.await();
                    ids.add(jobs.start().id());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        go.countDown();
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        assertEquals(1, ids.size());
        assertEquals(1, tasks.size());
    }
}
