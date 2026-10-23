package com.ori_leibo.nbafantasyscout.client;

import com.ori_leibo.nbafantasyscout.domain.Player;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Reads ESPN's teams list and team rosters. Rosters are always the current season. */
public class EspnRosterMapper {

    private static final Logger log = LoggerFactory.getLogger(EspnRosterMapper.class);

    /** A bad team id means ESPN changed the format, so it fails the call instead of skipping a whole team. */
    public List<Long> teamIds(String teamsJson) {
        JsonNode root = JsonMapper.shared().readTree(teamsJson);
        List<Long> ids = new ArrayList<>();
        for (JsonNode entry : root.path("sports").path(0).path("leagues").path(0).path("teams")) {
            ids.add(Long.parseLong(entry.path("team").path("id").asString("")));
        }
        return ids;
    }

    public List<Player> players(String rosterJson) {
        JsonNode root = JsonMapper.shared().readTree(rosterJson);
        String team = textOrNull(root.path("team").path("abbreviation"));

        List<Player> players = new ArrayList<>();
        for (JsonNode athlete : root.path("athletes")) {
            toPlayer(athlete, team).ifPresent(players::add);
        }
        return players;
    }

    private static Optional<Player> toPlayer(JsonNode athlete, String team) {
        String name = athlete.path("fullName").asString("");
        long id;
        try {
            id = Long.parseLong(athlete.path("id").asString(""));
        } catch (NumberFormatException e) {
            log.warn("Skipping ESPN athlete \"{}\": no usable id", name);
            return Optional.empty();
        }
        if (name.isBlank()) {
            log.warn("Skipping ESPN athlete {}: no name", id);
            return Optional.empty();
        }
        return Optional.of(new Player(id, name, team,
                textOrNull(athlete.path("position").path("abbreviation")),
                // injuries is empty for a healthy player
                textOrNull(athlete.path("injuries").path(0).path("status"))));
    }

    private static String textOrNull(JsonNode node) {
        String text = node.asString("");
        return text.isBlank() ? null : text;
    }
}
