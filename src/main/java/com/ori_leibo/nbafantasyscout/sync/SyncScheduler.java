package com.ori_leibo.nbafantasyscout.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Starts the daily sync on a schedule ({@code scout.sync.cron}, in {@code scout.sync.zone}). It goes through
 * {@link SyncJobs} like a manual start, so the two never run at once and a scheduled run can be polled too.
 */
public class SyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(SyncScheduler.class);

    private final SyncJobs jobs;

    public SyncScheduler(SyncJobs jobs) {
        this.jobs = jobs;
    }

    @Scheduled(cron = "${scout.sync.cron}", zone = "${scout.sync.zone}")
    public void syncDaily() {
        log.info("Scheduled daily sync: job {}", jobs.start().id());
    }
}
