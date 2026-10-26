package com.ori_leibo.nbafantasyscout.persistence;

import com.ori_leibo.nbafantasyscout.domain.GameLog;
import java.util.Arrays;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The one hand-written SQL: JPA has no single-statement upsert. The daily sync re-fetches whole seasons,
 * so most games are already stored and unchanged; the UNIQUE (player_id, game_date) constraint routes them
 * to the UPDATE, and its WHERE skips the write unless ESPN changed the stats (a stat correction).
 */
@Repository
public class GameLogUpsert {

    // EXCLUDED is the row we tried to insert; IS DISTINCT FROM compares the whole tuple, null-safe
    private static final String UPSERT = """
            INSERT INTO game_logs (player_id, game_date, minutes, fgm, fga, fg3m, ftm, fta, reb, ast, stl, blk, tov, pts)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (player_id, game_date) DO UPDATE SET
                minutes = EXCLUDED.minutes, fgm = EXCLUDED.fgm, fga = EXCLUDED.fga, fg3m = EXCLUDED.fg3m,
                ftm = EXCLUDED.ftm, fta = EXCLUDED.fta, reb = EXCLUDED.reb, ast = EXCLUDED.ast,
                stl = EXCLUDED.stl, blk = EXCLUDED.blk, tov = EXCLUDED.tov, pts = EXCLUDED.pts
            WHERE (game_logs.minutes, game_logs.fgm, game_logs.fga, game_logs.fg3m, game_logs.ftm, game_logs.fta,
                   game_logs.reb, game_logs.ast, game_logs.stl, game_logs.blk, game_logs.tov, game_logs.pts)
                IS DISTINCT FROM
                  (EXCLUDED.minutes, EXCLUDED.fgm, EXCLUDED.fga, EXCLUDED.fg3m, EXCLUDED.ftm, EXCLUDED.fta,
                   EXCLUDED.reb, EXCLUDED.ast, EXCLUDED.stl, EXCLUDED.blk, EXCLUDED.tov, EXCLUDED.pts)
            """;

    private final JdbcTemplate jdbc;

    public GameLogUpsert(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Inserts new games and updates changed ones, in one batch; returns how many rows were new or changed. */
    public int upsert(long playerId, List<GameLog> games) {
        if (games.isEmpty()) {
            return 0;
        }
        int[][] counts = jdbc.batchUpdate(UPSERT, games, games.size(), (statement, game) -> {
            statement.setLong(1, playerId);
            statement.setObject(2, game.gameDate());
            statement.setInt(3, game.minutes());
            statement.setInt(4, game.fieldGoalsMade());
            statement.setInt(5, game.fieldGoalsAttempted());
            statement.setInt(6, game.threesMade());
            statement.setInt(7, game.freeThrowsMade());
            statement.setInt(8, game.freeThrowsAttempted());
            statement.setInt(9, game.rebounds());
            statement.setInt(10, game.assists());
            statement.setInt(11, game.steals());
            statement.setInt(12, game.blocks());
            statement.setInt(13, game.turnovers());
            statement.setInt(14, game.points());
        });
        // per row: 1 = inserted or updated, 0 = unchanged; needs real counts, so no reWriteBatchedInserts=true
        // (with it the driver reports -2 per row)
        return Arrays.stream(counts).flatMapToInt(Arrays::stream).sum();
    }
}
