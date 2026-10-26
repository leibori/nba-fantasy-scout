package com.ori_leibo.nbafantasyscout.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ori_leibo.nbafantasyscout.TestcontainersConfiguration;
import com.ori_leibo.nbafantasyscout.domain.Player;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class PlayerRepositoryTest {

    @Autowired
    private PlayerRepository players;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void savingAKnownPlayerAgainUpdatesTheRowInsteadOfAddingOne() {
        players.saveAndFlush(PlayerEntity.from(new Player(3945274, "Luka Doncic", "LAL", "G", null)));

        // next day's sync: same ESPN id, now injured
        players.saveAndFlush(PlayerEntity.from(new Player(3945274, "Luka Doncic", "LAL", "G", "Out")));

        // read with SQL, not JPA, so the check hits the table and not Hibernate's cache
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM players", Integer.class));
        assertEquals("Out", jdbc.queryForObject("SELECT status FROM players WHERE id = 3945274", String.class));
    }

    @Test
    void clearsTheTeamOfPlayersNoLongerOnARoster() {
        players.saveAllAndFlush(List.of(
                PlayerEntity.from(new Player(1, "Still Rostered", "LAL", "G", null)),
                PlayerEntity.from(new Player(2, "Waived Player", "BOS", "F", null))));

        assertEquals(1, players.clearTeamExcept(List.of(1L)));

        assertEquals(List.of("LAL"), jdbc.queryForList("SELECT team FROM players WHERE team IS NOT NULL", String.class));
    }
}
