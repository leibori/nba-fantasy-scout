package com.ori_leibo.nbafantasyscout.sync;

/**
 * Published after every sync, also one that aborted part-way: it has still stored new data.
 * {@code result} is null when the sync aborted.
 */
public record SyncCompletedEvent(SyncService.Result result) {

    public boolean aborted() {
        return result == null;
    }
}
