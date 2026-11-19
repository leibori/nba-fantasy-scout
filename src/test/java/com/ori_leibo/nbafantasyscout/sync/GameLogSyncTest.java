package com.ori_leibo.nbafantasyscout.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ori_leibo.nbafantasyscout.client.NbaDataClient;
import com.ori_leibo.nbafantasyscout.domain.GameLog;
import com.ori_leibo.nbafantasyscout.domain.Player;
import com.ori_leibo.nbafantasyscout.persistence.GameLogRepository;
import com.ori_leibo.nbafantasyscout.persistence.GameLogUpsert;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GameLogSyncTest {

    private static final int SEASON = 2026;
    private static final Player LUKA = new Player(1, "Luka Dončić", "LAL", "G", null, null);
    private static final Player JOKIC = new Player(2, "Nikola Jokić", "DEN", "C", null, null);
    private static final Player SGA = new Player(3, "Shai Gilgeous-Alexander", "OKC", "G", null, null);
    private static final Player FLAGG = new Player(4, "Cooper Flagg", "DAL", "F", null, 0);    private static final List<GameLog> GAMES = List.of(
            new GameLog(LocalDate.of(2026, 1, 2), 36, 10, 21, 4, 7, 9, 11, 6, 2, 1, 3, 31));

    @Mock
    private NbaDataClient nba;
    @Mock
    private GameLogUpsert gameLogUpsert;
    @Mock
    private GameLogRepository gameLogs;

    private GameLogSync sync;

    @BeforeEach
    void setUp() {
        // Runnable::run: tasks run on the calling thread, so the order of calls is predictable
        // a day in the 2025-26 season
        Clock clock = Clock.fixed(Instant.parse("2026-01-15T12:00:00Z"), ZoneOffset.UTC);
        sync = new GameLogSync(nba, gameLogUpsert, gameLogs, Runnable::run, clock, 2);
        // by default every player already has last season stored; the backfill test overrides this
        lenient().when(gameLogs.findPlayerIdsWithGamesBetween(GameLog.seasonStart(SEASON - 1), GameLog.seasonEnd(SEASON - 1)))
                .thenReturn(Set.of(LUKA.id(), JOKIC.id(), SGA.id()));
    }

    @Test
    void fetchesLastSeasonOnlyForNonRookiesWhoDoNotHaveItYet() {
        when(gameLogs.findPlayerIdsWithGamesBetween(GameLog.seasonStart(SEASON - 1), GameLog.seasonEnd(SEASON - 1)))
                .thenReturn(Set.of(LUKA.id()));
        when(nba.fetchGameLogs(anyLong(), eq(SEASON))).thenReturn(List.of());
        when(nba.fetchGameLogs(JOKIC.id(), SEASON - 1)).thenReturn(GAMES);

        // Jokic: unknown experience, still fetched; Flagg: rookie, nothing to fetch
        GameLogSync.Result result = sync.sync(List.of(LUKA, JOKIC, FLAGG));

        assertEquals(1, result.lastSeasonFetched());
        verify(gameLogUpsert).upsert(JOKIC.id(), GAMES);
        verify(nba, never()).fetchGameLogs(LUKA.id(), SEASON - 1);
        verify(nba, never()).fetchGameLogs(FLAGG.id(), SEASON - 1);
    }

    @Test
    void skipsAPlayerWhoseGameLogsFailAndKeepsGoing() {
        when(nba.fetchGameLogs(LUKA.id(), SEASON)).thenReturn(GAMES);
        when(nba.fetchGameLogs(JOKIC.id(), SEASON)).thenThrow(new IllegalStateException("ESPN returned HTTP 500"));
        when(nba.fetchGameLogs(SGA.id(), SEASON)).thenReturn(GAMES);
        when(gameLogUpsert.upsert(anyLong(), anyList())).thenReturn(1);

        GameLogSync.Result result = sync.sync(List.of(LUKA, JOKIC, SGA));

        assertEquals(new GameLogSync.Result(2, 0, 1), result);
        verify(gameLogUpsert).upsert(SGA.id(), GAMES);
    }

    @Test
    void abortsWhenFailuresComeInARow() {
        when(nba.fetchGameLogs(anyLong(), anyInt())).thenThrow(new IllegalStateException("ESPN returned HTTP 503"));

        assertThrows(IllegalStateException.class, () -> sync.sync(List.of(LUKA, JOKIC, SGA)));

        verify(nba, times(2)).fetchGameLogs(anyLong(), anyInt());
    }
}
