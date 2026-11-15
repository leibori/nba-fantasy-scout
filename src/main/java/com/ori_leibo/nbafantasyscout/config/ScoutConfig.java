package com.ori_leibo.nbafantasyscout.config;

import com.ori_leibo.nbafantasyscout.client.EspnGameLogMapper;
import com.ori_leibo.nbafantasyscout.client.EspnNbaDataClient;
import com.ori_leibo.nbafantasyscout.client.EspnRosterMapper;
import com.ori_leibo.nbafantasyscout.client.HttpFetcher;
import com.ori_leibo.nbafantasyscout.client.NbaDataClient;
import com.ori_leibo.nbafantasyscout.client.SleeperClient;
import com.ori_leibo.nbafantasyscout.client.SleeperMapper;
import com.ori_leibo.nbafantasyscout.persistence.GameLogRepository;
import com.ori_leibo.nbafantasyscout.persistence.GameLogUpsert;
import com.ori_leibo.nbafantasyscout.persistence.PlayerRepository;
import com.ori_leibo.nbafantasyscout.persistence.TrendingSnapshotRepository;
import com.ori_leibo.nbafantasyscout.recommend.RecommendationService;
import com.ori_leibo.nbafantasyscout.recommend.TrendCacheRefresher;
import com.ori_leibo.nbafantasyscout.recommend.TrendService;
import com.ori_leibo.nbafantasyscout.roster.PlayerMatcher;
import com.ori_leibo.nbafantasyscout.sync.GameLogSync;
import com.ori_leibo.nbafantasyscout.sync.SyncService;
import com.ori_leibo.nbafantasyscout.sync.TrendingSync;
import com.ori_leibo.nbafantasyscout.trend.FormClassifier;
import com.ori_leibo.nbafantasyscout.trend.HotStreakScoring;
import com.ori_leibo.nbafantasyscout.trend.TrendCalculator;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

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

    // fetching is network-bound, so more threads than cores is fine; the ESPN rate limit is the real cap
    @Bean
    ThreadPoolTaskExecutor syncExecutor(ScoutProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.sync().threads());
        executor.setMaxPoolSize(properties.sync().threads());
        executor.setThreadNamePrefix("sync-");
        executor.setDaemon(true); // idle pool threads must not keep the JVM alive (the demo run exits when done)
        executor.setTaskDecorator(new MdcTaskDecorator());
        return executor;
    }

    @Bean
    NbaDataClient nbaDataClient(HttpClient httpClient, ScoutProperties properties) {
        ScoutProperties.Espn espn = properties.espn();
        HttpFetcher http = httpFetcher(httpClient, "ESPN", espn.timeout(), espn.maxCallsPerSecond(), properties.http());
        return new EspnNbaDataClient(http, espn.teamsBaseUrl(), espn.gameLogBaseUrl(),
                new EspnRosterMapper(), new EspnGameLogMapper());
    }

    @Bean
    SleeperClient sleeperClient(HttpClient httpClient, ScoutProperties properties) {
        ScoutProperties.Sleeper sleeper = properties.sleeper();
        HttpFetcher http = httpFetcher(httpClient, "Sleeper", sleeper.timeout(), sleeper.maxCallsPerSecond(),
                properties.http());
        return new SleeperClient(http, sleeper.baseUrl(), sleeper.lookbackHours(), sleeper.limit(), new SleeperMapper());
    }

    // each source gets its own retry and rate limiter, so Sleeper calls never use up ESPN's permits
    private static HttpFetcher httpFetcher(HttpClient httpClient, String source, Duration timeout, int maxCallsPerSecond,
                                           ScoutProperties.Http http) {
        Retry retry = Retry.of(source, RetryConfig.custom()
                .maxAttempts(http.maxAttempts())
                .intervalFunction(IntervalFunction.ofExponentialBackoff(http.initialBackoff(), 2))
                .retryOnException(HttpFetcher::isRetryable)
                .build());
        RateLimiter rateLimiter = RateLimiter.of(source, RateLimiterConfig.custom()
                .limitForPeriod(maxCallsPerSecond)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ofSeconds(10)) // how long a thread may wait for a permit
                .build());
        return new HttpFetcher(httpClient, source, timeout, retry, rateLimiter);
    }

    @Bean
    PlayerMatcher playerMatcher(ScoutProperties properties) {
        return new PlayerMatcher(properties.matcher().minConfidence());
    }

    @Bean
    GameLogSync gameLogSync(NbaDataClient nbaDataClient, GameLogUpsert gameLogUpsert, GameLogRepository gameLogs,
                            Executor syncExecutor, ScoutProperties properties) {
        return new GameLogSync(nbaDataClient, gameLogUpsert, gameLogs, syncExecutor,
                properties.sync().season(), properties.sync().maxConsecutiveFailures());
    }

    @Bean
    TrendingSync trendingSync(SleeperClient sleeperClient, PlayerMatcher playerMatcher, TrendingSnapshotRepository snapshots) {
        return new TrendingSync(sleeperClient, playerMatcher, snapshots);
    }

    @Bean
    SyncService syncService(NbaDataClient nbaDataClient, PlayerRepository players, GameLogSync gameLogSync,
                            TrendingSync trendingSync, Clock clock, ApplicationEventPublisher events) {
        return new SyncService(nbaDataClient, players, gameLogSync, trendingSync, clock, events);
    }

    @Bean
    TrendCacheRefresher trendCacheRefresher(CacheManager cacheManager, TrendService trendService) {
        // looked up once, so a wrong cache name fails at startup, not at the end of a sync
        Cache forms = Objects.requireNonNull(cacheManager.getCache(TrendService.CACHE), "no cache " + TrendService.CACHE);
        return new TrendCacheRefresher(forms, trendService);
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
