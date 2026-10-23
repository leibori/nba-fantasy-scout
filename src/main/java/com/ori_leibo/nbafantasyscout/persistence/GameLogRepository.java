package com.ori_leibo.nbafantasyscout.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameLogRepository extends JpaRepository<GameLogEntity, Long> {

    // Spring Data derives the SQL from the name: WHERE player_id = ? ORDER BY game_date ASC
    List<GameLogEntity> findByPlayerIdOrderByGameDateAsc(long playerId);
}
