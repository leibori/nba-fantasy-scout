package com.ori_leibo.nbafantasyscout.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ori_leibo.nbafantasyscout.client.SleeperClient;
import com.ori_leibo.nbafantasyscout.domain.Player;
import com.ori_leibo.nbafantasyscout.domain.TrendingSnapshot;
import com.ori_leibo.nbafantasyscout.domain.TrendingSnapshot.Type;
import com.ori_leibo.nbafantasyscout.persistence.TrendingSnapshotEntity;
import com.ori_leibo.nbafantasyscout.persistence.TrendingSnapshotRepository;
import com.ori_leibo.nbafantasyscout.roster.PlayerMatcher;
import java.time.Instant;
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
class TrendingSyncTest {

    private static final Instant NOW = Instant.parse("2026-10-26T06:00:00Z");
    private static final Player LUKA = new Player(1, "Luka Dončić", "LAL", "G", null, null);
    private static final Player JOKIC = new Player(2, "Nikola Jokić", "DEN", "C", null, null);

    @Mock
    private SleeperClient sleeper;
    @Mock
    private TrendingSnapshotRepository snapshots;
    @Captor
    private ArgumentCaptor<List<TrendingSnapshotEntity>> savedSnapshots;

    private TrendingSync sync;

    @BeforeEach
    void setUp() {
        sync = new TrendingSync(sleeper, new PlayerMatcher(0.85), snapshots);
    }

    @Test
    void storesTrendingPlayersWithASureMatchAndSkipsTheRest() {
        when(sleeper.fetchPlayerNames()).thenReturn(Map.of("s1", "Luka Doncic", "s2", "Nikola Jokic", "s3", "Michael Jordan"));
        when(sleeper.fetchTrendingAdds()).thenReturn(Map.of("s1", 500, "s3", 40));
        when(sleeper.fetchTrendingDrops()).thenReturn(Map.of("s2", 120, "unknown", 7));

        int stored = sync.sync(List.of(LUKA, JOKIC), NOW);

        assertEquals(2, stored);
        verify(snapshots).saveAll(savedSnapshots.capture());
        assertEquals(List.of(new TrendingSnapshot(LUKA.id(), Type.ADD, 500, NOW), new TrendingSnapshot(JOKIC.id(), Type.DROP, 120, NOW)),
                savedSnapshots.getValue().stream().map(TrendingSnapshotEntity::toSnapshot).toList());
    }

    @Test
    void storesNothingAndDoesNotThrowWhenSleeperIsDown() {
        when(sleeper.fetchPlayerNames()).thenThrow(new IllegalStateException("Sleeper returned HTTP 502"));

        assertEquals(0, sync.sync(List.of(LUKA), NOW));

        verifyNoInteractions(snapshots);
    }
}
