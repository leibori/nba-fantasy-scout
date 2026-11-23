package com.ori_leibo.nbafantasyscout.client;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ori_leibo.nbafantasyscout.domain.GameLog;
import com.ori_leibo.nbafantasyscout.domain.Player;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

// Calls the real sources once each. ESPN and the Yahoo read-only host are undocumented, so this catches a changed
// format before users do. Not part of `./gradlew test`; run with `./gradlew liveTest` (also a daily CI run).
@Tag("live")
class LiveSourcesTest {

    private static final String ESPN_TEAMS = "https://site.api.espn.com/apis/site/v2/sports/basketball/nba";
    private static final String ESPN_GAME_LOGS = "https://site.web.api.espn.com/apis/common/v3/sports/basketball/nba";
    private static final String SLEEPER = "https://api.sleeper.app/v1";
    private static final String YAHOO = "https://pub-api-ro.fantasysports.yahoo.com/fantasy/v2";
    private static final long PUBLIC_LEAGUE = 246889; // the league the test fixtures come from

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Test
    void espnPlayersGameLogsAndScheduleStillMap() {
        EspnNbaDataClient espn = new EspnNbaDataClient(fetcher("ESPN", 10), ESPN_TEAMS, ESPN_GAME_LOGS,
                new EspnRosterMapper(), new EspnGameLogMapper(), new EspnScoreboardMapper());

        List<Player> players = espn.fetchPlayers();
        assertTrue(players.size() > 400, "only " + players.size() + " players on the 30 rosters");
        assertTrue(players.stream().allMatch(p -> p.name() != null && p.team() != null));

        // last season always has finished games, whatever the time of year; some of the first veterans played
        int lastSeason = GameLog.seasonOf(LocalDate.now()) - 1;
        assertTrue(players.stream()
                        .filter(p -> p.experienceYears() != null && p.experienceYears() > 1)
                        .limit(5)
                        .anyMatch(p -> !espn.fetchGameLogs(p.id(), lastSeason).isEmpty()),
                "no game logs last season for five veterans");

        assertDoesNotThrow(() -> espn.fetchSchedule(LocalDate.now())); // may be empty off-season, must still parse
    }

    @Test
    void sleeperTrendingAndNamesStillMap() {
        SleeperClient sleeper = new SleeperClient(fetcher("Sleeper", 5), SLEEPER, 24, 25, new SleeperMapper());

        assertDoesNotThrow(sleeper::fetchTrendingAdds);
        assertTrue(sleeper.fetchPlayerNames().size() > 1000);
    }

    @Test
    void yahooPublicLeagueStillReadsWithoutLogin() {
        YahooClient yahoo = new YahooClient(fetcher("Yahoo", 2), YAHOO, 25, new YahooMapper());

        YahooMapper.League league = yahoo.league(PUBLIC_LEAGUE);
        assertNotNull(league.scoring());

        List<YahooMapper.Team> teams = yahoo.teams(PUBLIC_LEAGUE);
        assertFalse(teams.isEmpty());
        assertFalse(yahoo.roster(teams.getFirst().key()).isEmpty());
        assertFalse(yahoo.freeAgents(PUBLIC_LEAGUE).isEmpty());
    }

    private static HttpFetcher fetcher(String source, int callsPerSecond) {
        Retry retry = Retry.of(source, RetryConfig.custom()
                .maxAttempts(3)
                .intervalFunction(IntervalFunction.ofExponentialBackoff(Duration.ofSeconds(1), 2))
                .retryOnException(HttpFetcher::isRetryable)
                .build());
        RateLimiter limiter = RateLimiter.of(source, RateLimiterConfig.custom()
                .limitForPeriod(callsPerSecond)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ofSeconds(30))
                .build());
        return new HttpFetcher(HTTP_CLIENT, source, Duration.ofSeconds(20), retry, limiter);
    }
}
