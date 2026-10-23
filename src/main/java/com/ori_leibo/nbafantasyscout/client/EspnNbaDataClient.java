package com.ori_leibo.nbafantasyscout.client;

import com.ori_leibo.nbafantasyscout.domain.GameLog;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Adapter for ESPN's unofficial API (free, no key). Only does HTTP; parsing is in {@link EspnGameLogMapper}. */
public class EspnNbaDataClient implements NbaDataClient {

    private static final Logger log = LoggerFactory.getLogger(EspnNbaDataClient.class);

    private final HttpClient httpClient;
    private final String baseUrl;
    private final Duration timeout;
    private final EspnGameLogMapper mapper;

    public EspnNbaDataClient(HttpClient httpClient, String baseUrl, Duration timeout, EspnGameLogMapper mapper) {
        this.httpClient = httpClient;
        this.baseUrl = baseUrl;
        this.timeout = timeout;
        this.mapper = mapper;
    }

    @Override
    public List<GameLog> fetchGameLogs(long playerId, int season) {
        URI uri = URI.create("%s/athletes/%d/gamelog?season=%d".formatted(baseUrl, playerId, season));
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri).timeout(timeout).GET().build());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("ESPN returned HTTP " + response.statusCode() + " for " + uri);
        }
        List<GameLog> games = mapper.map(response.body());
        log.debug("Fetched {} games for player {} season {}", games.size(), playerId, season);
        return games;
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new UncheckedIOException("ESPN request failed: " + request.uri(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while calling " + request.uri(), e);
        }
    }
}
