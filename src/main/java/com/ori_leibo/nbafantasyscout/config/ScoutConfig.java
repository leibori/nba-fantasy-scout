package com.ori_leibo.nbafantasyscout.config;

import com.ori_leibo.nbafantasyscout.client.EspnGameLogMapper;
import com.ori_leibo.nbafantasyscout.client.EspnNbaDataClient;
import com.ori_leibo.nbafantasyscout.client.EspnRosterMapper;
import com.ori_leibo.nbafantasyscout.client.NbaDataClient;
import com.ori_leibo.nbafantasyscout.client.SleeperClient;
import com.ori_leibo.nbafantasyscout.client.SleeperMapper;
import com.ori_leibo.nbafantasyscout.persistence.GameLogRepository;
import com.ori_leibo.nbafantasyscout.persistence.GameLogUpsert;
import com.ori_leibo.nbafantasyscout.persistence.PlayerRepository;
import com.ori_leibo.nbafantasyscout.persistence.TrendingSnapshotRepository;
import com.ori_leibo.nbafantasyscout.recommend.RecommendationService;
import com.ori_leibo.nbafantasyscout.recommend.TrendService;
import com.ori_leibo.nbafantasyscout.roster.PlayerMatcher;
import com.ori_leibo.nbafantasyscout.sync.SyncService;
import com.ori_leibo.nbafantasyscout.trend.FormClassifier;
import com.ori_leibo.nbafantasyscout.trend.HotStreakScoring;
import com.ori_leibo.nbafantasyscout.trend.TrendCalculator;
import java.net.http.HttpClient;
import java.time.Clock;
import java.util.Objects;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Builds the plain-Java clients and services from {@link ScoutProperties}; Spring only wires them. */
@Configuration
@EnableCaching
@EnableConfigurationProperties(ScoutProperties.class)
public class ScoutConfig {

    // one JDK HttpClient (connection pool) shared by ESPN and Sleeper
    @Bean
    HttpClient httpClient() {
        return HttpClient.newHttpClient();
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    NbaDataClient nbaDataClient(HttpClient httpClient, ScoutProperties properties) {
        ScoutProperties.Espn espn = properties.espn();
        return new EspnNbaDataClient(httpClient, espn.teamsBaseUrl(), espn.gameLogBaseUrl(), espn.timeout(),
                new EspnRosterMapper(), new EspnGameLogMapper());
    }

    @Bean
    SleeperClient sleeperClient(HttpClient httpClient, ScoutProperties properties) {
        ScoutProperties.Sleeper sleeper = properties.sleeper();
        return new SleeperClient(httpClient, sleeper.baseUrl(), sleeper.timeout(), sleeper.lookbackHours(),
                sleeper.limit(), new SleeperMapper());
    }

    @Bean
    PlayerMatcher playerMatcher(ScoutProperties properties) {
        return new PlayerMatcher(properties.matcher().minConfidence());
    }

    @Bean
    SyncService syncService(NbaDataClient nbaDataClient, SleeperClient sleeperClient, PlayerMatcher playerMatcher,
                            PlayerRepository players, GameLogUpsert gameLogUpsert, GameLogRepository gameLogs,
                            TrendingSnapshotRepository snapshots, Clock clock, ScoutProperties properties,
                            CacheManager cacheManager) {
        // looked up once, so a wrong cache name fails at startup, not at the end of a sync
        Cache forms = Objects.requireNonNull(cacheManager.getCache(TrendService.CACHE), "no cache " + TrendService.CACHE);
        return new SyncService(nbaDataClient, sleeperClient, playerMatcher, players, gameLogUpsert, gameLogs, snapshots, clock,
                properties.sync().season(), properties.sync().maxConsecutiveFailures(), forms::clear);
    }

    @Bean
    TrendCalculator trendCalculator(ScoutProperties properties) {
        ScoutProperties.Trend trend = properties.trend();
        return new TrendCalculator(trend.window(), trend.minBaselineGames(), trend.minMinutes());
    }

    @Bean
    HotStreakScoring hotStreakScoring(ScoutProperties properties) {
        ScoutProperties.HotStreak weights = properties.hotStreak();
        return new HotStreakScoring(weights.minutesWeight(), weights.fieldGoalsAttemptedWeight(), weights.opportunityWeight());
    }

    @Bean
    FormClassifier formClassifier(HotStreakScoring hotStreakScoring, ScoutProperties properties) {
        ScoutProperties.Trend trend = properties.trend();
        return new FormClassifier(hotStreakScoring, trend.hotThreshold(), trend.highValue(), trend.earlyColdMinMinutes(),
                trend.earlyColdMinMinutesDrop());
    }

    @Bean
    TrendService trendService(PlayerRepository players, GameLogRepository gameLogs, TrendingSnapshotRepository snapshots,
                              TrendCalculator trendCalculator, HotStreakScoring hotStreakScoring,
                              FormClassifier formClassifier, ScoutProperties properties) {
        return new TrendService(players, gameLogs, snapshots, trendCalculator, hotStreakScoring, formClassifier,
                properties.sync().season(), properties.trend().leagueMinMinutes(), properties.trend().percentageWeight());
    }

    @Bean
    RecommendationService recommendationService(TrendService trendService) {
        return new RecommendationService(trendService);
    }
}
