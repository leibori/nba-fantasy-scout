package com.ori_leibo.nbafantasyscout.client;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Sleeper's free API, used only for crowd signals (trending adds/drops). Only does HTTP; parsing is in the mapper. */
public class SleeperClient {

    private static final Logger log = LoggerFactory.getLogger(SleeperClient.class);

    private final HttpClient httpClient;
    private final String baseUrl;
    private final Duration timeout;
    private final int lookbackHours;
    private final int limit;
    private final SleeperMapper mapper;

    public SleeperClient(HttpClient httpClient, String baseUrl, Duration timeout, int lookbackHours, int limit,
                         SleeperMapper mapper) {
        this.httpClient = httpClient;
        this.baseUrl = baseUrl;
        this.timeout = timeout;
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
        Map<String, String> names = mapper.playerNames(get(baseUrl + "/players/nba"));
        log.info("Fetched {} player names from Sleeper", names.size());
        return names;
    }

    private Map<String, Integer> fetchTrending(String type) {
        Map<String, Integer> counts = mapper.trending(
                get("%s/players/nba/trending/%s?lookback_hours=%d&limit=%d".formatted(baseUrl, type, lookbackHours, limit)));
        log.info("Fetched {} trending {}s from Sleeper", counts.size(), type);
        return counts;
    }

    // TODO: same get/send as EspnNbaDataClient; pull both into one HTTP helper when retries and rate limiting go in
    private String get(String url) {
        URI uri = URI.create(url);
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri).timeout(timeout).GET().build());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Sleeper returned HTTP " + response.statusCode() + " for " + uri);
        }
        return response.body();
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new UncheckedIOException("Sleeper request failed: " + request.uri(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while calling " + request.uri(), e);
        }
    }
}
