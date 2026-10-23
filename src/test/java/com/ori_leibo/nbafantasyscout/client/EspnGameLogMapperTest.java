package com.ori_leibo.nbafantasyscout.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ori_leibo.nbafantasyscout.domain.GameLog;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

class EspnGameLogMapperTest {

    private final EspnGameLogMapper mapper = new EspnGameLogMapper();

    // trimmed real response: LeBron 2025-26, 3 regular season games + 1 playoff game
    private static String fixture() throws IOException {
        try (InputStream in = EspnGameLogMapperTest.class.getResourceAsStream("/espn/gamelog.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void keepsRegularSeasonOnlyWithUtcDatesConvertedToUsDates() throws IOException {
        List<LocalDate> dates = mapper.map(fixture()).stream()
                .map(GameLog::gameDate)
                .sorted()
                .toList();

        // 2026-04-01T02:30Z is a March 31 evening game; the playoff game on 2026-05-11 is dropped
        assertEquals(List.of(LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 10), LocalDate.of(2026, 4, 12)), dates);
    }

    @Test
    void mapsStatsByLabelIncludingMadeAttemptedPairs() throws IOException {
        GameLog game = mapper.map(fixture()).stream()
                .max(Comparator.comparing(GameLog::gameDate))
                .orElseThrow();

        // ESPN row: MIN 17, FG 6-15, 3PT 0-3, FT 6-7, REB 4, AST 6, BLK 0, STL 3, PF 0, TO 4, PTS 18
        assertEquals(new GameLog(LocalDate.of(2026, 4, 12), 17, 6, 15, 0, 6, 7, 4, 6, 3, 0, 4, 18), game);
    }

    @Test
    void skipsMalformedRowsAndKeepsTheRest() {
        String json = """
                {
                  "labels": ["MIN","FG","FG%","3PT","3P%","FT","FT%","REB","AST","BLK","STL","PF","TO","PTS"],
                  "seasonTypes": [{
                    "displayName": "2025-26 Regular Season",
                    "categories": [{"type": "event", "events": [
                      {"eventId": "1", "stats": ["--","--","--","--","--","--","--","--","--","--","--","--","--","--"]},
                      {"eventId": "2", "stats": ["30","5-10","50.0","1-2","50.0","2-2","100.0","7","3","1","1","2","2","13"]},
                      {"eventId": "3", "stats": ["30","5-10","50.0","1-2","50.0","2-2","100.0","7","3","1","1","2","2","13"]}
                    ]}]
                  }],
                  "events": {
                    "1": {"gameDate": "2026-01-10T00:00:00.000+00:00"},
                    "2": {"gameDate": "2026-01-12T00:00:00.000+00:00"}
                  }
                }
                """;

        // event 1 has no numbers, event 3 has no date: only event 2 survives
        List<GameLog> games = mapper.map(json);

        assertEquals(1, games.size());
        assertEquals(LocalDate.of(2026, 1, 11), games.getFirst().gameDate());
    }
}
