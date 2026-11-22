package com.ori_leibo.nbafantasyscout.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ori_leibo.nbafantasyscout.domain.ScheduledGame;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

// saved real scoreboards, trimmed to the fields read: 23 Nov 2026 before tip-off, and 22 Nov with a game under way
class EspnScoreboardMapperTest {

    private final EspnScoreboardMapper mapper = new EspnScoreboardMapper();

    private static String fixture(String name) throws IOException {
        try (InputStream in = EspnScoreboardMapperTest.class.getResourceAsStream("/espn/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void readsEachGamesTeamsAndWhetherItHasStarted() throws IOException {
        List<ScheduledGame> upcoming = mapper.games(fixture("scoreboard-upcoming.json"));
        assertEquals(10, upcoming.size());
        assertEquals(new ScheduledGame("CHA", "MEM", false), upcoming.getFirst());
        assertTrue(upcoming.stream().noneMatch(ScheduledGame::started));

        // a game under way is no longer a game left to play
        assertEquals(List.of(new ScheduledGame("ATL", "DET", true), new ScheduledGame("MIA", "OKC", false),
                new ScheduledGame("WSH", "ORL", false)), mapper.games(fixture("scoreboard-live.json")));

        // preseason games and an empty day
        assertEquals(List.of(), mapper.games("""
                {"events": [{"season": {"type": 1}, "status": {"type": {"state": "pre"}},
                  "competitions": [{"competitors": [{"homeAway": "home", "team": {"abbreviation": "BOS"}},
                                                    {"homeAway": "away", "team": {"abbreviation": "NY"}}]}]}]}"""));
        assertEquals(List.of(), mapper.games("{\"events\": []}"));
    }
}
