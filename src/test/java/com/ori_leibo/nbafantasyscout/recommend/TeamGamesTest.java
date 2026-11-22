package com.ori_leibo.nbafantasyscout.recommend;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ori_leibo.nbafantasyscout.domain.Player;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TeamGamesTest {

    private static Player player(long id, String team) {
        return new Player(id, "Player " + id, team, "G", null, 3);
    }

    private static List<LocalDate> november(Integer... days) {
        return Arrays.stream(days).map(day -> LocalDate.of(2026, 11, day)).toList();
    }

    @Test
    void countsTheTeamGamesSinceAPlayersLastGameIgnoringATradedPlayersOldTeam() {
        Map<Player, List<LocalDate>> played = new HashMap<>();
        played.put(player(1, "LAL"), november(1, 3, 5, 7));
        played.put(player(2, "LAL"), november(1, 3, 5, 7));
        played.put(player(3, "LAL"), november(1, 3, 5, 7));
        played.put(player(4, "LAL"), november(1, 3));
        // traded in from Boston: his Boston games on the 2nd and 4th are not Lakers games
        played.put(player(5, "LAL"), november(2, 4, 7));
        played.put(player(6, null), november(1, 2, 3));

        TeamGames games = new TeamGames(played);

        assertEquals(2, games.missedSince("LAL", LocalDate.of(2026, 11, 3)), "the 5th and the 7th, not the 4th");
        assertEquals(0, games.missedSince("LAL", LocalDate.of(2026, 11, 7)));
        assertEquals(0, games.missedSince("BOS", LocalDate.of(2026, 11, 1)), "no games known for that team");
        assertEquals(0, games.missedSince(null, LocalDate.of(2026, 11, 1)));
        assertEquals(0, games.missedSince("LAL", null), "no game this season yet");
    }
}
