package com.ori_leibo.nbafantasyscout.persistence;

import com.ori_leibo.nbafantasyscout.domain.GameLog;
import java.util.Arrays;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The one hand-written SQL: JPA has no "insert unless it exists". The daily sync re-fetches whole seasons,
 * so most games are already stored; the UNIQUE (player_id, game_date) constraint makes Postgres skip them.
 */
@Repository
public class GameLogUpsert {

    private static final String INSERT_IF_NEW = """
            INSERT INTO game_logs (player_id, game_date, minutes, fgm, fga, fg3m, ftm, fta, reb, ast, stl, blk, tov, pts)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (player_id, game_date) DO NOTHING
            """;

    private final JdbcTemplate jdbc;

    public GameLogUpsert(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Inserts the games not stored yet, in one batch; returns how many were new. */
    public int insertNew(long playerId, List<GameLog> games) {
        if (games.isEmpty()) {
            return 0;
        }
        int[][] counts = jdbc.batchUpdate(INSERT_IF_NEW, games, games.size(), (statement, game) -> {
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
        // per row: 1 = inserted, 0 = skipped by ON CONFLICT
        return Arrays.stream(counts).flatMapToInt(Arrays::stream).sum();
    }
}
