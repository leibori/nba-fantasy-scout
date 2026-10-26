package com.ori_leibo.nbafantasyscout.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrendingSnapshotRepository extends JpaRepository<TrendingSnapshotEntity, Long> {

    // WHERE player_id = ? ORDER BY fetched_at ASC: one player's crowd history, oldest first
    List<TrendingSnapshotEntity> findByPlayerIdOrderByFetchedAtAsc(long playerId);
}
