package com.ori_leibo.nbafantasyscout.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrendingSnapshotRepository extends JpaRepository<TrendingSnapshotEntity, Long> {

    // WHERE player_id = ? ORDER BY fetched_at ASC: one player's crowd history, oldest first
    List<TrendingSnapshotEntity> findByPlayerIdOrderByFetchedAtAsc(long playerId);

    // ORDER BY fetched_at DESC LIMIT 1: any row of the latest sync, to learn its time
    Optional<TrendingSnapshotEntity> findFirstByOrderByFetchedAtDesc();

    // every row of one sync: they all share the sync's fetched_at
    List<TrendingSnapshotEntity> findByFetchedAt(Instant fetchedAt);
}
