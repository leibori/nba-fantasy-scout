package com.ori_leibo.nbafantasyscout.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Everything under {@code scout:} in application.yml, bound once at startup. */
@ConfigurationProperties("scout")
public record ScoutProperties(Espn espn, Sleeper sleeper, Sync sync, Matcher matcher, Trend trend, HotStreak hotStreak) {

    public record Espn(String teamsBaseUrl, String gameLogBaseUrl, Duration timeout) {
    }

    public record Sleeper(String baseUrl, Duration timeout, int lookbackHours, int limit) {
    }

    /** {@code season} is the year the season ends (2026 = 2025-26). */
    public record Sync(int season, int maxConsecutiveFailures) {
    }

    public record Matcher(double minConfidence) {
    }

    /**
     * Trend window and noise filter, form thresholds, and the value league: players averaging at least
     * {@code leagueMinMinutes} this season.
     */
    public record Trend(int window, int minBaselineGames, double minMinutes, double hotThreshold, double highValue,
                        double earlyColdMinMinutes, double earlyColdMinMinutesDrop, double leagueMinMinutes,
                        double percentageWeight) {
    }

    public record HotStreak(double minutesWeight, double fieldGoalsAttemptedWeight, double opportunityWeight) {
    }
}
