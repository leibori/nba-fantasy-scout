package com.ori_leibo.nbafantasyscout.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ori_leibo.nbafantasyscout.client.NbaDataClient;
import com.ori_leibo.nbafantasyscout.domain.Player;
import com.ori_leibo.nbafantasyscout.persistence.PlayerRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SyncServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-26T06:00:00Z");
    private static final List<Player> ROSTER = List.of(
            new Player(1, "Luka Dončić", "LAL", "G", null, null),
            new Player(2, "Nikola Jokić", "DEN", "C", null, null));

    @Mock
    private NbaDataClient nba;
    @Mock
    private PlayerRepository players;
    @Mock
    private GameLogSync gameLogSync;
    @Mock
    private TrendingSync trendingSync;

    private SyncService sync;
    private final List<Object> events = new ArrayList<>();

    @BeforeEach
    void setUp() {
        sync = new SyncService(nba, players, gameLogSync, trendingSync, Clock.fixed(NOW, ZoneOffset.UTC), events::add);
    }

    @Test
    void storesThePlayersThenRunsBothStepsAndAddsUpTheResult() {
        when(nba.fetchPlayers()).thenReturn(ROSTER);
        when(gameLogSync.sync(ROSTER)).thenReturn(new GameLogSync.Result(5, 1, 1));
        when(trendingSync.sync(ROSTER, NOW)).thenReturn(3);

        SyncService.Result result = sync.syncDaily("sync-test");

        assertEquals(List.of(2, 5, 1, 1, 3), List.of(result.players(), result.gamesChanged(), result.lastSeasonFetched(),
                result.failedPlayers(), result.snapshots()));
        verify(players).clearTeamExcept(List.of(1L, 2L));
        assertEquals(List.of(new SyncCompletedEvent(result)), events);
    }

    @Test
    void anAbortedSyncSkipsTrendingButStillPublishesTheEvent() {
        when(nba.fetchPlayers()).thenReturn(ROSTER);
        when(gameLogSync.sync(ROSTER)).thenThrow(new IllegalStateException("Sync aborted; ESPN looks down"));

        assertThrows(IllegalStateException.class, () -> sync.syncDaily("sync-test"));

        verifyNoInteractions(trendingSync);
        assertEquals(List.of(new SyncCompletedEvent(null)), events,
                "an aborted sync still stored data, so listeners must hear of it");
    }

    @Test
    void refusesAnEmptyRosterInsteadOfClearingEveryTeam() {
        when(nba.fetchPlayers()).thenReturn(List.of());

        assertThrows(IllegalStateException.class, () -> sync.syncDaily("sync-test"));

        verify(players, never()).clearTeamExcept(anyList());
        verifyNoInteractions(gameLogSync, trendingSync);
    }
}
