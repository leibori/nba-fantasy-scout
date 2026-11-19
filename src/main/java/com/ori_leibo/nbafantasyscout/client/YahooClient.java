package com.ori_leibo.nbafantasyscout.client;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Yahoo Fantasy's read-only host, which answers for public leagues without a login (undocumented, so it may change;
 * typed names stay the fallback). Only does HTTP; parsing is in the mapper. "nba" is the current season's game.
 */
public class YahooClient {

    private static final Logger log = LoggerFactory.getLogger(YahooClient.class);
    private static final int PAGE_SIZE = 25; // Yahoo's maximum per request

    private final HttpFetcher http;
    private final String baseUrl;
    private final int maxFreeAgents;
    private final YahooMapper mapper;

    /** {@code maxFreeAgents}: the best ones are enough; weaker free agents are never suggested anyway. */
    public YahooClient(HttpFetcher http, String baseUrl, int maxFreeAgents, YahooMapper mapper) {
        this.http = http;
        this.baseUrl = baseUrl;
        this.maxFreeAgents = maxFreeAgents;
        this.mapper = mapper;
    }

    public YahooMapper.League league(long leagueId) {
        return mapper.league(http.get("%s/league/nba.l.%d/settings?format=json".formatted(baseUrl, leagueId)));
    }

    public List<YahooMapper.Team> teams(long leagueId) {
        return mapper.teams(http.get("%s/league/nba.l.%d/teams?format=json".formatted(baseUrl, leagueId)));
    }

    public List<String> roster(String teamKey) {
        return mapper.playerNames(http.get("%s/team/%s/roster?format=json".formatted(baseUrl, teamKey)));
    }

    /** Available players, Yahoo's actual-rank order (best first), up to {@code maxFreeAgents}. */
    public List<String> freeAgents(long leagueId) {
        List<String> names = new ArrayList<>();
        for (int start = 0; start < maxFreeAgents; start += PAGE_SIZE) {
            List<String> page = mapper.playerNames(http.get(
                    "%s/league/nba.l.%d/players;status=A;sort=AR;start=%d;count=%d?format=json"
                            .formatted(baseUrl, leagueId, start, Math.min(PAGE_SIZE, maxFreeAgents - start))));
            names.addAll(page);
            if (page.size() < PAGE_SIZE) {
                break; // past the last free agent
            }
        }
        log.info("Fetched {} free agents from Yahoo league {}", names.size(), leagueId);
        return names;
    }
}
