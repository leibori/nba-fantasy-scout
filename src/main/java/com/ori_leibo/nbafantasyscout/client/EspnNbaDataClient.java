package com.ori_leibo.nbafantasyscout.client;

import com.ori_leibo.nbafantasyscout.domain.GameLog;
import com.ori_leibo.nbafantasyscout.domain.Player;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adapter for ESPN's unofficial API (free, no key). Only does HTTP; parsing is in the mappers.
 * ESPN serves teams/rosters and game logs from two different hosts, hence two base URLs.
 */
public class EspnNbaDataClient implements NbaDataClient {

    private static final Logger log = LoggerFactory.getLogger(EspnNbaDataClient.class);

    private final HttpClient httpClient;
    private final String teamsBaseUrl;
    private final String gameLogBaseUrl;
    private final Duration timeout;
    private final EspnRosterMapper rosterMapper;
    private final EspnGameLogMapper gameLogMapper;

    public EspnNbaDataClient(HttpClient httpClient, String teamsBaseUrl, String gameLogBaseUrl, Duration timeout,
                             EspnRosterMapper rosterMapper, EspnGameLogMapper gameLogMapper) {
        this.httpClient = httpClient;
        this.teamsBaseUrl = teamsBaseUrl;
        this.gameLogBaseUrl = gameLogBaseUrl;
        this.timeout = timeout;
        this.rosterMapper = rosterMapper;
        this.gameLogMapper = gameLogMapper;
    }

    @Override
    public List<Player> fetchPlayers() {
        List<Player> players = new ArrayList<>();
        for (long teamId : rosterMapper.teamIds(get(teamsBaseUrl + "/teams"))) {
            players.addAll(rosterMapper.players(get("%s/teams/%d/roster".formatted(teamsBaseUrl, teamId))));
        }
        log.info("Fetched {} players from ESPN rosters", players.size());
        return players;
    }

    @Override
    public List<GameLog> fetchGameLogs(long playerId, int season) {
        String json = get("%s/athletes/%d/gamelog?season=%d".formatted(gameLogBaseUrl, playerId, season));
        List<GameLog> games = gameLogMapper.map(json);
        log.debug("Fetched {} games for player {} season {}", games.size(), playerId, season);
        return games;
    }

    private String get(String url) {
        URI uri = URI.create(url);
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri).timeout(timeout).GET().build());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("ESPN returned HTTP " + response.statusCode() + " for " + uri);
        }
        return response.body();
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
