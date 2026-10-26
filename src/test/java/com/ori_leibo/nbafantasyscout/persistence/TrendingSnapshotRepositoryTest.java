package com.ori_leibo.nbafantasyscout.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ori_leibo.nbafantasyscout.TestcontainersConfiguration;
import com.ori_leibo.nbafantasyscout.domain.Player;
import com.ori_leibo.nbafantasyscout.domain.TrendingSnapshot;
import com.ori_leibo.nbafantasyscout.domain.TrendingSnapshot.Type;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class TrendingSnapshotRepositoryTest {

    @Autowired
    private TrendingSnapshotRepository snapshots;
    @Autowired
    private PlayerRepository players;

    @Test
    void storesBothTypesAndReadsAPlayersHistoryOldestFirst() {
        players.save(PlayerEntity.from(new Player(1, "Alex Caruso", "OKC", "G", null)));
        // Postgres keeps microseconds, so the test uses whole seconds to compare exactly
        TrendingSnapshot today = new TrendingSnapshot(1, Type.DROP, 120, Instant.parse("2026-10-26T06:00:00Z"));
        TrendingSnapshot yesterday = new TrendingSnapshot(1, Type.ADD, 30276, Instant.parse("2026-10-25T06:00:00Z"));
        snapshots.saveAllAndFlush(List.of(TrendingSnapshotEntity.from(today), TrendingSnapshotEntity.from(yesterday)));

        List<TrendingSnapshot> history = snapshots.findByPlayerIdOrderByFetchedAtAsc(1).stream()
                .map(TrendingSnapshotEntity::toSnapshot)
                .toList();

        assertEquals(List.of(yesterday, today), history);
    }
}
