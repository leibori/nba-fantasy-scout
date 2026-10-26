package com.ori_leibo.nbafantasyscout.client;

import com.ori_leibo.nbafantasyscout.domain.GameLog;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns ESPN's gamelog JSON into regular season {@link GameLog}s.
 * Stats are string arrays ordered like {@code labels}; game dates live in {@code events[eventId]}.
 */
public class EspnGameLogMapper {

    private static final Logger log = LoggerFactory.getLogger(EspnGameLogMapper.class);

    // ESPN times are UTC; a 7:30 PM Eastern game is already "tomorrow" in UTC
    private static final ZoneId US_EASTERN = ZoneId.of("America/New_York");

    public List<GameLog> map(String json) {
        JsonNode root = JsonMapper.shared().readTree(json);
        // a player with no games that season (rookie, injured all year) gets only a "filters" block
        if (root.path("seasonTypes").isEmpty()) {
            return List.of();
        }
        Columns columns = Columns.from(root.path("labels"));

        JsonNode events = root.path("events");

        List<GameLog> games = new ArrayList<>();
        for (JsonNode seasonType : root.path("seasonTypes")) {
            if (!isRegularSeason(seasonType)) {
                continue;
            }
            // the "total" category has no events and is skipped naturally
            for (JsonNode month : seasonType.path("categories")) {
                for (JsonNode row : month.path("events")) {
                    if (countsForFantasy(events.path(row.path("eventId").asString("")))) {
                        toGameLog(row, events, columns).ifPresent(games::add);
                    }
                }
            }
        }
        return games;
    }

    private static boolean isRegularSeason(JsonNode seasonType) {
        return seasonType.path("displayName").asString("").endsWith("Regular Season");
    }

    /**
     * ESPN files these under "Regular Season" although fantasy leagues don't count them: the All-Star games
     * (up to 3 on one day, which also breaks the one-game-per-date key), the Play-In and the NBA Cup final.
     */
    private static boolean countsForFantasy(JsonNode event) {
        String note = event.path("eventNote").asString("");
        return !note.startsWith("NBA All-Star") && !note.startsWith("NBA Play-In") && !note.equals("NBA Cup Championship");
    }

    private static Optional<GameLog> toGameLog(JsonNode row, JsonNode events, Columns columns) {
        String eventId = row.path("eventId").asString("");
        JsonNode stats = row.path("stats");
        try {
            String fieldGoals = stat(stats, columns.fieldGoals());
            String threes = stat(stats, columns.threes());
            String freeThrows = stat(stats, columns.freeThrows());
            return Optional.of(new GameLog(
                    usDate(events.path(eventId).path("gameDate").asString("")),
                    number(stats, columns.minutes()),
                    made(fieldGoals), attempted(fieldGoals),
                    made(threes),
                    made(freeThrows), attempted(freeThrows),
                    number(stats, columns.rebounds()),
                    number(stats, columns.assists()),
                    number(stats, columns.steals()),
                    number(stats, columns.blocks()),
                    number(stats, columns.turnovers()),
                    number(stats, columns.points())));
        } catch (NumberFormatException | DateTimeParseException e) {
            log.warn("Skipping ESPN game {}: {}", eventId, e.getMessage());
            return Optional.empty();
        }
    }

    private static LocalDate usDate(String utcTimestamp) {
        return OffsetDateTime.parse(utcTimestamp).atZoneSameInstant(US_EASTERN).toLocalDate();
    }

    private static String stat(JsonNode stats, int column) {
        return stats.path(column).asString("");
    }

    private static int number(JsonNode stats, int column) {
        return Integer.parseInt(stat(stats, column));
    }

    // ESPN writes shooting as "made-attempted", e.g. "8-18"
    private static int made(String madeAttempted) {
        return Integer.parseInt(madeAttempted.substring(0, dashIndex(madeAttempted)));
    }

    private static int attempted(String madeAttempted) {
        return Integer.parseInt(madeAttempted.substring(dashIndex(madeAttempted) + 1));
    }

    private static int dashIndex(String madeAttempted) {
        int dash = madeAttempted.indexOf('-');
        if (dash < 0) {
            throw new NumberFormatException("Not a made-attempted value: \"" + madeAttempted + "\"");
        }
        return dash;
    }

    /** Column positions looked up by label, so a reordered response still parses. */
    private record Columns(int minutes, int fieldGoals, int threes, int freeThrows,
                           int rebounds, int assists, int steals, int blocks, int turnovers, int points) {

        static Columns from(JsonNode labels) {
            List<String> names = labels.values().stream().map(JsonNode::asString).toList();
            return new Columns(
                    indexOf(names, "MIN"), indexOf(names, "FG"), indexOf(names, "3PT"), indexOf(names, "FT"),
                    indexOf(names, "REB"), indexOf(names, "AST"), indexOf(names, "STL"), indexOf(names, "BLK"),
                    indexOf(names, "TO"), indexOf(names, "PTS"));
        }

        // a missing label means ESPN changed the format: fail the whole call instead of storing wrong numbers
        private static int indexOf(List<String> names, String label) {
            int index = names.indexOf(label);
            if (index < 0) {
                throw new IllegalStateException("ESPN gamelog has no " + label + " column, labels: " + names);
            }
            return index;
        }
    }
}
