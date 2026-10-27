package com.ori_leibo.nbafantasyscout.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ori_leibo.nbafantasyscout.TestcontainersConfiguration;
import com.ori_leibo.nbafantasyscout.domain.GameLog;
import com.ori_leibo.nbafantasyscout.domain.Player;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, GameLogUpsert.class})
class GameLogRepositoryTest {

    @Autowired
    private GameLogRepository gameLogs;
    @Autowired
    private GameLogUpsert upsert;
    @Autowired
    private PlayerRepository players;

    @Test
    void findsPlayersWithGamesInADateRangeOnlyOnce() {
        players.saveAllAndFlush(List.of(player(1), player(2), player(3)));
        upsert.upsert(1, List.of(game(2026, 1, 5), game(2026, 3, 9)));  // last season, twice
        upsert.upsert(2, List.of(game(2026, 10, 25)));                  // this season only
        upsert.upsert(3, List.of(game(2025, 7, 31), game(2026, 8, 1))); // just outside on both sides

        assertEquals(Set.of(1L), gameLogs.findPlayerIdsWithGamesBetween(LocalDate.of(2025, 8, 1), LocalDate.of(2026, 7, 31)));
    }

    private static PlayerEntity player(long id) {
        return PlayerEntity.from(new Player(id, "Player " + id, "LAL", "G", null));
    }

    private static GameLog game(int year, int month, int day) {
        return new GameLog(LocalDate.of(year, month, day), 30, 8, 18, 2, 6, 8, 12, 3, 1, 0, 4, 20);
    }
}
