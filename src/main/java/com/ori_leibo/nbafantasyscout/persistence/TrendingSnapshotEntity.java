package com.ori_leibo.nbafantasyscout.persistence;

import com.ori_leibo.nbafantasyscout.domain.TrendingSnapshot;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Row of {@code trending_snapshots}. Rows are only appended, never updated. */
@Entity
@Table(name = "trending_snapshots")
public class TrendingSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private long playerId;
    @Enumerated(EnumType.STRING)
    private TrendingSnapshot.Type type;
    private int count;
    private Instant fetchedAt;

    protected TrendingSnapshotEntity() {
        // for JPA
    }

    private TrendingSnapshotEntity(long playerId, TrendingSnapshot.Type type, int count, Instant fetchedAt) {
        this.playerId = playerId;
        this.type = type;
        this.count = count;
        this.fetchedAt = fetchedAt;
    }

    public static TrendingSnapshotEntity from(TrendingSnapshot snapshot) {
        return new TrendingSnapshotEntity(snapshot.playerId(), snapshot.type(), snapshot.count(), snapshot.fetchedAt());
    }

    public TrendingSnapshot toSnapshot() {
        return new TrendingSnapshot(playerId, type, count, fetchedAt);
    }
}
