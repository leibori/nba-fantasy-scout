package com.ori_leibo.nbafantasyscout.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GameLogRepository extends JpaRepository<GameLogEntity, Long> {

    // Spring Data derives the SQL from the name: WHERE player_id = ? ORDER BY game_date ASC
    List<GameLogEntity> findByPlayerIdOrderByGameDateAsc(long playerId);

    // JPQL: one query for the whole roster instead of one "exists" per player
    @Query("SELECT DISTINCT g.playerId FROM GameLogEntity g WHERE g.gameDate BETWEEN :from AND :to")
    Set<Long> findPlayerIdsWithGamesBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
