package com.ori_leibo.nbafantasyscout.config;

import com.ori_leibo.nbafantasyscout.domain.LeagueScoring;
import com.ori_leibo.nbafantasyscout.domain.PointStat;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Everything under {@code scout:} in application.yml, bound once at startup. */
@ConfigurationProperties("scout")
public record ScoutProperties(Espn espn, Sleeper sleeper, Http http, Sync sync, Matcher matcher, Trend trend, HotStreak hotStreak,
                             Scoring scoring) {

    public record Espn(String teamsBaseUrl, String gameLogBaseUrl, Duration timeout, int maxCallsPerSecond) {
    }

    public record Sleeper(String baseUrl, Duration timeout, int maxCallsPerSecond, int lookbackHours, int limit) {
    }

    /** Retries for both sources: {@code maxAttempts} includes the first call; the wait doubles after each one. */
    public record Http(int maxAttempts, Duration initialBackoff) {
    }

    /** {@code season} is the year the season ends (2026 = 2025-26). */
    public record Sync(int season, int threads, int maxConsecutiveFailures) {
    }

    public record Matcher(double minConfidence) {
    }

    /**
     * Trend window and noise filter, form thresholds, and the value league: players averaging at least
     * {@code leagueMinMinutes} this season.
     */
    public record Trend(int window, int minBaselineGames, double minMinutes, double hotThreshold,
                        double earlyColdMinMinutes, double earlyColdMinMinutesDrop, double leagueMinMinutes,
                        double percentageWeight) {
    }

    public record HotStreak(double minutesWeight, double fieldGoalsAttemptedWeight, double opportunityWeight) {
    }

    /** League format when no league is given, the points league's point values, and "good right now" per format. */
    public record Scoring(LeagueScoring.Format format, Map<PointStat, Double> pointValues, HighValue highValue) {

        public record HighValue(double categories, double points) {
        }
    }
}
