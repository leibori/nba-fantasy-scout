package com.ori_leibo.nbafantasyscout.config;

import com.ori_leibo.nbafantasyscout.domain.LeagueScoring;
import com.ori_leibo.nbafantasyscout.domain.PointStat;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Everything under {@code scout:} in application.yml, bound once at startup. */
@ConfigurationProperties("scout")
public record ScoutProperties(Espn espn, Sleeper sleeper, Yahoo yahoo, Http http, Sync sync, Matcher matcher, Trend trend, HotStreak hotStreak,
                             Scoring scoring, Roster roster) {

    public record Espn(String teamsBaseUrl, String gameLogBaseUrl, Duration timeout, int maxCallsPerSecond) {
    }

    public record Sleeper(String baseUrl, Duration timeout, int maxCallsPerSecond, int lookbackHours, int limit) {
    }

    /** {@code maxFreeAgents}: how many of a league's best free agents to read (25 per request). */
    public record Yahoo(String baseUrl, Duration timeout, int maxCallsPerSecond, int maxFreeAgents) {
    }

    /** Retries for every source: {@code maxAttempts} includes the first call; the wait doubles after each one. */
    public record Http(int maxAttempts, Duration initialBackoff) {
    }

    public record Sync(int threads, int maxConsecutiveFailures) {
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

    /**
     * League format when no league is given, the points league's point values, and per format: "good right now"
     * and how much more value a free agent needs before dropping a player for him.
     */
    public record Scoring(LeagueScoring.Format format, Map<PointStat, Double> pointValues, PerFormat highValue,
                          PerFormat dropGap) {

        /** One threshold per league format; the formats score on different scales. */
        public record PerFormat(double categories, double points) {
        }
    }

    /** {@code pickUpNow}: how many free agents the roster advice suggests picking up. */
    public record Roster(int pickUpNow) {
    }
}
