package com.ori_leibo.nbafantasyscout.sync;

import com.ori_leibo.nbafantasyscout.client.SleeperClient;
import com.ori_leibo.nbafantasyscout.domain.Player;
import com.ori_leibo.nbafantasyscout.domain.TrendingSnapshot;
import com.ori_leibo.nbafantasyscout.persistence.TrendingSnapshotEntity;
import com.ori_leibo.nbafantasyscout.persistence.TrendingSnapshotRepository;
import com.ori_leibo.nbafantasyscout.roster.PlayerMatcher;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stores Sleeper's trending adds and drops as snapshots. Sleeper has no ESPN ids, so players are matched by name;
 * a player without a sure match is skipped.
 */
public class TrendingSync {

    private static final Logger log = LoggerFactory.getLogger(TrendingSync.class);

    private final SleeperClient sleeper;
    private final PlayerMatcher matcher;
    private final TrendingSnapshotRepository snapshots;

    public TrendingSync(SleeperClient sleeper, PlayerMatcher matcher, TrendingSnapshotRepository snapshots) {
        this.sleeper = sleeper;
        this.matcher = matcher;
        this.snapshots = snapshots;
    }

    /** The number of snapshots stored; crowd data is evidence only, so a Sleeper outage stores none and does not throw. */
    public int sync(List<Player> roster, Instant fetchedAt) {
        try {
            Map<String, String> names = sleeper.fetchPlayerNames();
            List<TrendingSnapshotEntity> rows = new ArrayList<>();
            rows.addAll(toSnapshots(sleeper.fetchTrendingAdds(), TrendingSnapshot.Type.ADD, names, roster, fetchedAt));
            rows.addAll(toSnapshots(sleeper.fetchTrendingDrops(), TrendingSnapshot.Type.DROP, names, roster, fetchedAt));
            snapshots.saveAll(rows);
            return rows.size();
        } catch (RuntimeException e) {
            log.error("Sleeper trending sync failed, continuing without crowd data", e);
            return 0;
        }
    }

    private List<TrendingSnapshotEntity> toSnapshots(Map<String, Integer> counts, TrendingSnapshot.Type type,
                                                     Map<String, String> names, List<Player> roster, Instant fetchedAt) {
        List<TrendingSnapshotEntity> rows = new ArrayList<>();
        counts.forEach((sleeperId, count) -> {
            String name = names.get(sleeperId);
            Optional<PlayerMatcher.Match> match = name == null ? Optional.empty() : matcher.match(name, roster);
            if (match.isEmpty() || match.get().unsure()) {
                log.warn("Skipping Sleeper {} of \"{}\" ({}): no sure ESPN match, best was {}", type, name, sleeperId, match);
                return;
            }
            rows.add(TrendingSnapshotEntity.from(
                    new TrendingSnapshot(match.get().player().id(), type, count, fetchedAt)));
        });
        return rows;
    }
}
