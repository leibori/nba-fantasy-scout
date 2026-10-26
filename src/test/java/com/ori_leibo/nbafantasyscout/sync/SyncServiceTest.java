package com.ori_leibo.nbafantasyscout.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ori_leibo.nbafantasyscout.client.NbaDataClient;
import com.ori_leibo.nbafantasyscout.client.SleeperClient;
import com.ori_leibo.nbafantasyscout.domain.GameLog;
import com.ori_leibo.nbafantasyscout.domain.Player;
import com.ori_leibo.nbafantasyscout.domain.TrendingSnapshot;
import com.ori_leibo.nbafantasyscout.domain.TrendingSnapshot.Type;
import com.ori_leibo.nbafantasyscout.persistence.GameLogUpsert;
import com.ori_leibo.nbafantasyscout.persistence.PlayerRepository;
import com.ori_leibo.nbafantasyscout.persistence.TrendingSnapshotEntity;
import com.ori_leibo.nbafantasyscout.persistence.TrendingSnapshotRepository;
import com.ori_leibo.nbafantasyscout.roster.PlayerMatcher;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SyncServiceTest {

    private static final int SEASON = 2026;
    private static final Instant NOW = Instant.parse("2026-10-26T06:00:00Z");
    private static final Player LUKA = new Player(1, "Luka Dončić", "LAL", "G", null);
    private static final Player JOKIC = new Player(2, "Nikola Jokić", "DEN", "C", null);
    private static final Player SGA = new Player(3, "Shai Gilgeous-Alexander", "OKC", "G", null);
    private static final List<GameLog> GAMES = List.of(
            new GameLog(LocalDate.of(2026, 1, 2), 36, 10, 21, 4, 7, 9, 11, 6, 2, 1, 3, 31));

    @Mock
    private NbaDataClient nba;
    @Mock
    private SleeperClient sleeper;
    @Mock
    private PlayerRepository players;
    @Mock
    private GameLogUpsert gameLogUpsert;
    @Mock
    private TrendingSnapshotRepository snapshots;
    @Captor
    private ArgumentCaptor<List<TrendingSnapshotEntity>> savedSnapshots;

    private SyncService sync;

    @BeforeEach
    void setUp() {
        sync = new SyncService(nba, sleeper, new PlayerMatcher(0.85), players, gameLogUpsert, snapshots,
                Clock.fixed(NOW, ZoneOffset.UTC), SEASON, 2);
    }

    @Test
    void skipsAPlayerWhoseGameLogsFailAndKeepsGoing() {
        when(nba.fetchPlayers()).thenReturn(List.of(LUKA, JOKIC, SGA));
        when(nba.fetchGameLogs(LUKA.id(), SEASON)).thenReturn(GAMES);
        when(nba.fetchGameLogs(JOKIC.id(), SEASON)).thenThrow(new IllegalStateException("ESPN returned HTTP 500"));
        when(nba.fetchGameLogs(SGA.id(), SEASON)).thenReturn(GAMES);
        when(gameLogUpsert.upsert(anyLong(), anyList())).thenReturn(1);

        SyncService.Result result = sync.syncDaily();

        assertEquals(3, result.players());
        assertEquals(2, result.gamesChanged());
        assertEquals(1, result.failedPlayers());
        verify(gameLogUpsert).upsert(SGA.id(), GAMES);
        verify(players).clearTeamExcept(List.of(1L, 2L, 3L));
    }

    @Test
    void abortsWhenFailuresComeInARow() {
        when(nba.fetchPlayers()).thenReturn(List.of(LUKA, JOKIC, SGA));
        when(nba.fetchGameLogs(anyLong(), anyInt())).thenThrow(new IllegalStateException("ESPN returned HTTP 503"));

        assertThrows(IllegalStateException.class, () -> sync.syncDaily());

        verify(nba, times(2)).fetchGameLogs(anyLong(), anyInt());
        verifyNoInteractions(sleeper, snapshots);
    }

    @Test
    void refusesAnEmptyRosterInsteadOfClearingEveryTeam() {
        when(nba.fetchPlayers()).thenReturn(List.of());

        assertThrows(IllegalStateException.class, () -> sync.syncDaily());

        verify(players, never()).clearTeamExcept(anyList());
    }

    @Test
    void storesTrendingPlayersWithASureMatchAndSkipsTheRest() {
        when(nba.fetchPlayers()).thenReturn(List.of(LUKA, JOKIC));
        when(sleeper.fetchPlayerNames()).thenReturn(Map.of("s1", "Luka Doncic", "s2", "Nikola Jokic", "s3", "Michael Jordan"));
        when(sleeper.fetchTrendingAdds()).thenReturn(Map.of("s1", 500, "s3", 40));
        when(sleeper.fetchTrendingDrops()).thenReturn(Map.of("s2", 120, "unknown", 7));

        SyncService.Result result = sync.syncDaily();

        assertEquals(2, result.snapshots());
        verify(snapshots).saveAll(savedSnapshots.capture());
        assertEquals(List.of(new TrendingSnapshot(LUKA.id(), Type.ADD, 500, NOW), new TrendingSnapshot(JOKIC.id(), Type.DROP, 120, NOW)),
                savedSnapshots.getValue().stream().map(TrendingSnapshotEntity::toSnapshot).toList());
    }

    @Test
    void finishesWithoutCrowdDataWhenSleeperIsDown() {
        when(nba.fetchPlayers()).thenReturn(List.of(LUKA));
        when(nba.fetchGameLogs(LUKA.id(), SEASON)).thenReturn(GAMES);
        when(gameLogUpsert.upsert(LUKA.id(), GAMES)).thenReturn(1);
        when(sleeper.fetchPlayerNames()).thenThrow(new IllegalStateException("Sleeper returned HTTP 502"));

        SyncService.Result result = sync.syncDaily();

        assertEquals(1, result.gamesChanged());
        assertEquals(0, result.snapshots());
        verifyNoInteractions(snapshots);
    }
}
