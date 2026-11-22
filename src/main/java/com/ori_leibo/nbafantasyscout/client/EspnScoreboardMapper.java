package com.ori_leibo.nbafantasyscout.client;

import com.ori_leibo.nbafantasyscout.domain.ScheduledGame;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Turns ESPN's scoreboard for one day into its regular season games, scheduled, live or over. */
public class EspnScoreboardMapper {

    // ESPN's season types: 1 preseason, 2 regular season, 3 playoffs
    private static final int REGULAR_SEASON = 2;

    public List<ScheduledGame> games(String json) {
        List<ScheduledGame> games = new ArrayList<>();
        for (JsonNode event : JsonMapper.shared().readTree(json).path("events")) {
            if (event.path("season").path("type").asInt() != REGULAR_SEASON) {
                continue;
            }
            String home = "";
            String away = "";
            for (JsonNode competitor : event.path("competitions").path(0).path("competitors")) {
                String team = competitor.path("team").path("abbreviation").asString("");
                if ("home".equals(competitor.path("homeAway").asString(""))) {
                    home = team;
                } else {
                    away = team;
                }
            }
            if (home.isEmpty() || away.isEmpty()) {
                continue; // a game whose teams aren't set yet, like a tournament slot
            }
            // "pre" = not started; "in" and "post" no longer count as games left
            boolean started = !"pre".equals(event.path("status").path("type").path("state").asString("pre"));
            games.add(new ScheduledGame(home, away, started));
        }
        return games;
    }
}
