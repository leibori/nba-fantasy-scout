package com.ori_leibo.nbafantasyscout.recommend;

import com.ori_leibo.nbafantasyscout.sync.SyncCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.context.event.EventListener;

/**
 * After every sync: drops the cached forms and computes them again, so the first request after a sync is fast.
 * Not {@code @CacheEvict} on the sync: that skips the eviction when the sync throws, and an aborted sync has
 * still stored new data.
 */
public class TrendCacheRefresher {

    private static final Logger log = LoggerFactory.getLogger(TrendCacheRefresher.class);

    private final Cache forms;
    // the Spring proxy, so the call below goes through @Cacheable and fills the cache
    private final TrendService trendService;

    public TrendCacheRefresher(Cache forms, TrendService trendService) {
        this.forms = forms;
        this.trendService = trendService;
    }

    @EventListener
    public void onSyncCompleted(SyncCompletedEvent event) {
        forms.clear();
        // runs in the sync's finally: a failure here must not hide the sync's own exception
        try {
            int players = trendService.formOfAllPlayers().size();
            log.info("Trend cache refreshed for {} players after {} sync", players, event.aborted() ? "an aborted" : "a");
        } catch (RuntimeException e) {
            log.error("Recomputing trends after the sync failed; the next request will try again", e);
        }
    }
}
