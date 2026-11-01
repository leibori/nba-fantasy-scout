package com.ori_leibo.nbafantasyscout.client;

import com.ori_leibo.nbafantasyscout.domain.GameLog;
import com.ori_leibo.nbafantasyscout.domain.Player;
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

    private final HttpFetcher http;
    private final String teamsBaseUrl;
    private final String gameLogBaseUrl;
    private final EspnRosterMapper rosterMapper;
    private final EspnGameLogMapper gameLogMapper;

    public EspnNbaDataClient(HttpFetcher http, String teamsBaseUrl, String gameLogBaseUrl,
                             EspnRosterMapper rosterMapper, EspnGameLogMapper gameLogMapper) {
        this.http = http;
        this.teamsBaseUrl = teamsBaseUrl;
        this.gameLogBaseUrl = gameLogBaseUrl;
        this.rosterMapper = rosterMapper;
        this.gameLogMapper = gameLogMapper;
    }

    @Override
    public List<Player> fetchPlayers() {
        List<Player> players = new ArrayList<>();
        for (long teamId : rosterMapper.teamIds(http.get(teamsBaseUrl + "/teams"))) {
            players.addAll(rosterMapper.players(http.get("%s/teams/%d/roster".formatted(teamsBaseUrl, teamId))));
        }
        log.info("Fetched {} players from ESPN rosters", players.size());
        return players;
    }

    @Override
    public List<GameLog> fetchGameLogs(long playerId, int season) {
        String json = http.get("%s/athletes/%d/gamelog?season=%d".formatted(gameLogBaseUrl, playerId, season));
        List<GameLog> games = gameLogMapper.map(json);
        log.debug("Fetched {} games for player {} season {}", games.size(), playerId, season);
        return games;
    }
}
