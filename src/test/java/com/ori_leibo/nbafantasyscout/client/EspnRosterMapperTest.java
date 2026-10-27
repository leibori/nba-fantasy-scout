package com.ori_leibo.nbafantasyscout.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ori_leibo.nbafantasyscout.domain.Player;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class EspnRosterMapperTest {

    private final EspnRosterMapper mapper = new EspnRosterMapper();

    private static String fixture(String name) throws IOException {
        try (InputStream in = EspnRosterMapperTest.class.getResourceAsStream("/espn/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void readsTeamIdsFromTeamsList() throws IOException {
        // trimmed real response: first 3 of the 30 teams; ids are ESPN's, not 1..30
        assertEquals(List.of(1L, 2L, 17L), mapper.teamIds(fixture("teams.json")));
    }

    @Test
    void readsPlayersWithTeamPositionInjuryStatusAndExperience() throws IOException {
        // trimmed real Lakers roster: one healthy player, one Out, one Day-To-Day; Carr is a rookie (0 years)
        assertEquals(List.of(
                        new Player(3945274, "Luka Doncic", "LAL", "G", null, 9),
                        new Player(5113969, "Cameron Carr", "LAL", "G", "Out", 0),
                        new Player(4592691, "Jake LaRavia", "LAL", "F", "Day-To-Day", 5)),
                mapper.players(fixture("roster.json")));
    }

    @Test
    void skipsAthletesWithoutUsableIdOrName() {
        String json = """
                {
                  "team": {"abbreviation": "LAL"},
                  "athletes": [
                    {"fullName": "No Id"},
                    {"id": "abc", "fullName": "Bad Id"},
                    {"id": "1"},
                    {"id": "2", "fullName": "No Position", "injuries": []}
                  ]
                }
                """;

        // only the last athlete is usable; a missing position is allowed and stays null
        assertEquals(List.of(new Player(2, "No Position", "LAL", null, null, null)), mapper.players(json));
    }
}
