package com.ori_leibo.nbafantasyscout.client;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Sleeper's free API, used only for crowd signals (trending adds/drops). Only does HTTP; parsing is in the mapper. */
public class SleeperClient {

    private static final Logger log = LoggerFactory.getLogger(SleeperClient.class);

    private final HttpFetcher http;
    private final String baseUrl;
    private final int lookbackHours;
    private final int limit;
    private final SleeperMapper mapper;

    public SleeperClient(HttpFetcher http, String baseUrl, int lookbackHours, int limit, SleeperMapper mapper) {
        this.http = http;
        this.baseUrl = baseUrl;
        this.lookbackHours = lookbackHours;
        this.limit = limit;
        this.mapper = mapper;
    }

    /** Sleeper id -> number of adds in the last {@code lookbackHours}, most added first. */
    public Map<String, Integer> fetchTrendingAdds() {
        return fetchTrending("add");
    }

    /** Sleeper id -> number of drops in the last {@code lookbackHours}, most dropped first. */
    public Map<String, Integer> fetchTrendingDrops() {
        return fetchTrending("drop");
    }

    /** Sleeper id -> full name for every NBA player. The response is ~2.5 MB: call it at most once per sync. */
    public Map<String, String> fetchPlayerNames() {
        Map<String, String> names = mapper.playerNames(http.get(baseUrl + "/players/nba"));
        log.info("Fetched {} player names from Sleeper", names.size());
        return names;
    }

    private Map<String, Integer> fetchTrending(String type) {
        Map<String, Integer> counts = mapper.trending(
                http.get("%s/players/nba/trending/%s?lookback_hours=%d&limit=%d".formatted(baseUrl, type, lookbackHours, limit)));
        log.info("Fetched {} trending {}s from Sleeper", counts.size(), type);
        return counts;
    }
}
