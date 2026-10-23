package com.ori_leibo.nbafantasyscout.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ori_leibo.nbafantasyscout.TestcontainersConfiguration;
import com.ori_leibo.nbafantasyscout.domain.GameLog;
import com.ori_leibo.nbafantasyscout.domain.Player;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

// real Postgres from Testcontainers instead of an embedded DB; each test rolls back
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, GameLogUpsert.class})
class GameLogUpsertTest {

    private static final long PLAYER_ID = 1;

    @Autowired
    private GameLogUpsert upsert;
    @Autowired
    private GameLogRepository gameLogs;
    @Autowired
    private PlayerRepository players;

    @BeforeEach
    void storePlayer() {
        // flush now: the JDBC insert below must see the player row for the foreign key
        players.saveAndFlush(PlayerEntity.from(new Player(PLAYER_ID, "Test Player", "LAL", "G", null)));
    }

    private static GameLog game(int day, int points) {
        return new GameLog(LocalDate.of(2026, 1, day), 30, 8, 18, 2, 6, 8, 12, 3, 1, 0, 4, points);
    }

    @Test
    void insertsOnlyGamesNotStoredYet() {
        assertEquals(2, upsert.insertNew(PLAYER_ID, List.of(game(1, 20), game(3, 24))));

        // day 1 is already stored, even with different stats; only day 2 is new
        assertEquals(1, upsert.insertNew(PLAYER_ID, List.of(game(1, 99), game(2, 18))));

        assertEquals(3, gameLogs.count());
    }

    @Test
    void readsGamesBackInDateOrderWithEveryStat() {
        GameLog lateGame = new GameLog(LocalDate.of(2026, 1, 9), 36, 10, 21, 4, 7, 9, 11, 6, 2, 3, 5, 31);
        upsert.insertNew(PLAYER_ID, List.of(lateGame, game(2, 18)));

        List<GameLog> stored = gameLogs.findByPlayerIdOrderByGameDateAsc(PLAYER_ID).stream()
                .map(GameLogEntity::toGameLog)
                .toList();

        assertEquals(List.of(game(2, 18), lateGame), stored);
    }
}
