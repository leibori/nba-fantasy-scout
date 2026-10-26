package com.ori_leibo.nbafantasyscout.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SleeperMapperTest {

    private final SleeperMapper mapper = new SleeperMapper();

    @Test
    void readsTrendingCountsInSleeperOrderSkippingRowsWithoutId() throws IOException {
        Map<String, Integer> counts = mapper.trending(json("trending.json"));

        assertEquals(List.of("2152", "2301", "1801"), List.copyOf(counts.keySet()));
        assertEquals(59800, counts.get("2152"));
    }

    @Test
    void readsPlayerNamesSkippingTeamEntries() throws IOException {
        assertEquals(Map.of("2152", "Precious Achiuwa", "1801", "Alex Caruso"), mapper.playerNames(json("players.json")));
    }

    private static String json(String file) throws IOException {
        try (InputStream in = SleeperMapperTest.class.getResourceAsStream("/sleeper/" + file)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
