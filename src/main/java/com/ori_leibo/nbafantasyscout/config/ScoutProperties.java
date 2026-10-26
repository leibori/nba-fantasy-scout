package com.ori_leibo.nbafantasyscout.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Everything under {@code scout:} in application.yml, bound once at startup. */
@ConfigurationProperties("scout")
public record ScoutProperties(Espn espn, Sleeper sleeper, Sync sync, Matcher matcher) {

    public record Espn(String teamsBaseUrl, String gameLogBaseUrl, Duration timeout) {
    }

    public record Sleeper(String baseUrl, Duration timeout, int lookbackHours, int limit) {
    }

    /** {@code season} is the year the season ends (2026 = 2025-26). */
    public record Sync(int season, int maxConsecutiveFailures) {
    }

    public record Matcher(double minConfidence) {
    }
}
