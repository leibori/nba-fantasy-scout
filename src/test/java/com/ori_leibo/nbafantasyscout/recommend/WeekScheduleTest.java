package com.ori_leibo.nbafantasyscout.recommend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ori_leibo.nbafantasyscout.client.NbaDataClient;
import com.ori_leibo.nbafantasyscout.domain.ScheduledGame;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WeekScheduleTest {

    // Wednesday 25 November 2026, 3 pm in New York
    private final MovableClock clock = new MovableClock(Instant.parse("2026-11-25T20:00:00Z"));

    @Mock
    private NbaDataClient nba;

    @Test
    void countsTheWeeksGamesAndTheGamesLeftFromTomorrowAndKeepsThemForAnHour() {
        when(nba.fetchSchedule(any())).thenReturn(List.of());
        when(nba.fetchSchedule(LocalDate.of(2026, 11, 23))).thenReturn(List.of(new ScheduledGame("BOS", "NY", true)));
        when(nba.fetchSchedule(LocalDate.of(2026, 11, 25))).thenReturn(List.of(new ScheduledGame("BOS", "LAL", false)));
        when(nba.fetchSchedule(LocalDate.of(2026, 11, 28))).thenReturn(List.of(new ScheduledGame("NY", "BOS", false)));
        WeekSchedule schedule = new WeekSchedule(nba, clock);

        WeekSchedule.Week week = schedule.thisWeek().orElseThrow();

        assertEquals(LocalDate.of(2026, 11, 23), week.start());
        assertEquals(LocalDate.of(2026, 11, 29), week.end());
        assertEquals(LocalDate.of(2026, 11, 26), week.from());
        // a pickup made today plays from tomorrow: Monday's game is played and tonight's is too soon
        assertEquals(new WeekSchedule.TeamWeek(3, 1), week.teams().get("BOS"));
        assertEquals(new WeekSchedule.TeamWeek(2, 1), week.teams().get("NY"));
        assertEquals(0, week.gamesLeft("LAL"), "its only game is tonight");
        assertEquals(0, week.gamesLeft("MIA"), "no game this week");
        assertEquals(0, week.gamesLeft(null));
        verify(nba, times(7)).fetchSchedule(any());

        clock.advance(Duration.ofMinutes(59));
        schedule.thisWeek();
        verify(nba, times(7)).fetchSchedule(any());
        clock.advance(Duration.ofMinutes(2));
        schedule.thisWeek();
        verify(nba, times(14)).fetchSchedule(any());
    }

    @Test
    void onSundayTheWeekThatCountsIsNextWeek() {
        clock.advance(Duration.ofDays(4));                                             // Sunday 29 November
        when(nba.fetchSchedule(any())).thenReturn(List.of());
        when(nba.fetchSchedule(LocalDate.of(2026, 11, 30))).thenReturn(List.of(new ScheduledGame("BOS", "NY", false)));

        WeekSchedule.Week week = new WeekSchedule(nba, clock).thisWeek().orElseThrow();

        assertEquals(LocalDate.of(2026, 11, 30), week.start());
        assertEquals(LocalDate.of(2026, 12, 6), week.end());
        assertEquals(1, week.gamesLeft("BOS"));
    }

    @Test
    void aFailedReadKeepsTheLastWeekAndNoWeekYetIsEmpty() {
        AtomicBoolean espnDown = new AtomicBoolean(true);
        when(nba.fetchSchedule(any())).thenAnswer(call -> {
            if (espnDown.get()) {
                throw new IllegalStateException("ESPN is down");
            }
            return List.of(new ScheduledGame("BOS", "NY", false));
        });
        WeekSchedule schedule = new WeekSchedule(nba, clock);

        assertTrue(schedule.thisWeek().isEmpty());

        espnDown.set(false);
        WeekSchedule.Week week = schedule.thisWeek().orElseThrow();
        espnDown.set(true);
        clock.advance(Duration.ofMinutes(61));
        assertSame(week, schedule.thisWeek().orElseThrow(), "an older count beats none");
    }

    private static final class MovableClock extends Clock {
        private Instant now;

        MovableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
