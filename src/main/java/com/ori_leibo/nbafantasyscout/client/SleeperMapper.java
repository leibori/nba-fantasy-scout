package com.ori_leibo.nbafantasyscout.client;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Reads Sleeper's trending list and player list. Sleeper ids are strings and have no ESPN id attached. */
public class SleeperMapper {

    private static final Logger log = LoggerFactory.getLogger(SleeperMapper.class);

    /** Sleeper id -> how many leagues added (or dropped) the player, in Sleeper's order (most first). */
    public Map<String, Integer> trending(String trendingJson) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (JsonNode row : JsonMapper.shared().readTree(trendingJson)) {
            String id = row.path("player_id").asString("");
            if (id.isBlank()) {
                log.warn("Skipping Sleeper trending row without player_id: {}", row);
                continue;
            }
            counts.put(id, row.path("count").asInt(0));
        }
        return counts;
    }

    /** Sleeper id -> full name. Entries without a full_name are teams (key "BKN" = Brooklyn Nets), not players. */
    public Map<String, String> playerNames(String playersJson) {
        Map<String, String> names = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> entry : JsonMapper.shared().readTree(playersJson).properties()) {
            String name = entry.getValue().path("full_name").asString("");
            if (!name.isBlank()) {
                names.put(entry.getKey(), name);
            }
        }
        return names;
    }
}
