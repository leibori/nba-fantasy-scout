package com.ori_leibo.nbafantasyscout.recommend;

import com.ori_leibo.nbafantasyscout.sync.SyncCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.context.event.EventListener;

/**
 * After every sync: drops the cached season data and loads it again, so the first request after a sync is fast.
 * Not {@code @CacheEvict} on the sync: that skips the eviction when the sync throws, and an aborted sync has
 * still stored new data.
 */
public class TrendCacheRefresher {

    private static final Logger log = LoggerFactory.getLogger(TrendCacheRefresher.class);

    private final Cache cache;
    // the Spring proxy, so the call below goes through @Cacheable and fills the cache
    private final SeasonData seasonData;

    public TrendCacheRefresher(Cache cache, SeasonData seasonData) {
        this.cache = cache;
        this.seasonData = seasonData;
    }

    @EventListener
    public void onSyncCompleted(SyncCompletedEvent event) {
        cache.clear();
        // runs in the sync's finally: a failure here must not hide the sync's own exception
        try {
            int players = seasonData.load().players().size();
            log.info("Trend cache refreshed for {} players after {} sync", players, event.aborted() ? "an aborted" : "a");
        } catch (RuntimeException e) {
            log.error("Recomputing trends after the sync failed; the next request will try again", e);
        }
    }
}
