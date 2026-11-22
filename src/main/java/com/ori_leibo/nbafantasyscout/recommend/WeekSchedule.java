package com.ori_leibo.nbafantasyscout.recommend;

import com.ori_leibo.nbafantasyscout.client.NbaDataClient;
import com.ori_leibo.nbafantasyscout.domain.ScheduledGame;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The fantasy week a pickup can still help in: in Yahoo a player added today plays from tomorrow, so the week is the
 * Monday-to-Sunday week (US time) that holds tomorrow, and a team's games left are its games from tomorrow to Sunday.
 * On a Sunday that is already next week. Read from the daily scoreboard and kept for an hour.
 */
public class WeekSchedule {

    private static final Logger log = LoggerFactory.getLogger(WeekSchedule.class);
    private static final ZoneId US = ZoneId.of("America/New_York");
    private static final Duration FRESH_FOR = Duration.ofHours(1);

    private final NbaDataClient nba;
    private final Clock clock;
    private Week cached;

    public WeekSchedule(NbaDataClient nba, Clock clock) {
        this.nba = nba;
        this.clock = clock;
    }

    /** {@code games}: in the whole week; {@code left}: from tomorrow on, the games a pickup can play. */
    public record TeamWeek(int games, int left) {
    }

    /** {@code from}: tomorrow, the first day counted as left. */
    public record Week(LocalDate start, LocalDate end, LocalDate from, Map<String, TeamWeek> teams, Instant readAt) {

        public Week {
            teams = Map.copyOf(teams);
        }

        /** 0 for a team without a game left this week, or without a team. */
        public int gamesLeft(String team) {
            TeamWeek week = team == null ? null : teams.get(team);
            return week == null ? 0 : week.left();
        }
    }

    /**
     * Empty only when the schedule has never been read: the advice works without it. A failed refresh keeps the last
     * week, since an hour-old count beats none. Synchronized: concurrent requests read the scoreboard once.
     */
    public synchronized Optional<Week> thisWeek() {
        Instant now = clock.instant();
        LocalDate tomorrow = LocalDate.ofInstant(now, US).plusDays(1);
        if (cached != null && now.isBefore(cached.readAt().plus(FRESH_FOR)) && cached.from().equals(tomorrow)) {
            return Optional.of(cached);
        }
        try {
            cached = read(tomorrow, now);
        } catch (RuntimeException e) {
            log.warn("Could not read this week's schedule, keeping the last one: {}", e.getMessage());
        }
        return Optional.ofNullable(cached);
    }

    private Week read(LocalDate tomorrow, Instant now) {
        LocalDate monday = tomorrow.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate sunday = monday.plusDays(6);
        Map<String, int[]> counts = new HashMap<>();
        // the whole week, so a team whose games are all played still shows, with none left
        for (LocalDate day = monday; !day.isAfter(sunday); day = day.plusDays(1)) {
            boolean pickupCanPlay = !day.isBefore(tomorrow);
            for (ScheduledGame game : nba.fetchSchedule(day)) {
                for (String team : new String[] {game.homeTeam(), game.awayTeam()}) {
                    int[] count = counts.computeIfAbsent(team, key -> new int[2]);
                    count[0]++;
                    if (pickupCanPlay && !game.started()) {
                        count[1]++;
                    }
                }
            }
        }
        Map<String, TeamWeek> teams = new HashMap<>();
        counts.forEach((team, count) -> teams.put(team, new TeamWeek(count[0], count[1])));
        log.info("Read the schedule for {} to {}: {} teams", monday, sunday, teams.size());
        return new Week(monday, sunday, tomorrow, teams, now);
    }
}
