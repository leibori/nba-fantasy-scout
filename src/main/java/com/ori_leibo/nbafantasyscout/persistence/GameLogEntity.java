package com.ori_leibo.nbafantasyscout.persistence;

import com.ori_leibo.nbafantasyscout.domain.GameLog;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * Row of {@code game_logs}, read-only from JPA: rows are written by {@link GameLogUpsert}.
 * Stats are {@code short} because the columns are SMALLINT and Hibernate validates the types.
 */
@Entity
@Table(name = "game_logs")
public class GameLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private long playerId;
    private LocalDate gameDate;
    private short minutes;
    private short fgm;
    private short fga;
    private short fg3m;
    private short ftm;
    private short fta;
    private short reb;
    private short ast;
    private short stl;
    private short blk;
    private short tov;
    private short pts;

    protected GameLogEntity() {
        // for JPA
    }

    public long playerId() {
        return playerId;
    }

    public GameLog toGameLog() {
        return new GameLog(gameDate, minutes, fgm, fga, fg3m, ftm, fta, reb, ast, stl, blk, tov, pts);
    }
}
