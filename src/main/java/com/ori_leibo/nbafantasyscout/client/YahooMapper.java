package com.ori_leibo.nbafantasyscout.client;

import com.ori_leibo.nbafantasyscout.domain.LeagueScoring;
import com.ori_leibo.nbafantasyscout.domain.PointStat;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns Yahoo Fantasy JSON into what the roster advice needs: the league's scoring, its teams, and players with their positions.
 * Yahoo's JSON is converted from XML: lists are objects keyed "0", "1", ... with a "count", and an entity is an
 * array of one-field objects ({"name": ...}, {"team_key": ...}).
 */
public class YahooMapper {

    // Yahoo abbreviations of the stats a points league can score that we can compute
    private static final Map<String, PointStat> POINT_STATS = Map.ofEntries(
            Map.entry("PTS", PointStat.PTS), Map.entry("REB", PointStat.REB), Map.entry("AST", PointStat.AST),
            Map.entry("ST", PointStat.STL), Map.entry("BLK", PointStat.BLK), Map.entry("TO", PointStat.TO),
            Map.entry("3PTM", PointStat.THREES), Map.entry("FGM", PointStat.FGM), Map.entry("FGA", PointStat.FGA),
            Map.entry("FTM", PointStat.FTM), Map.entry("FTA", PointStat.FTA));
    private static final Set<String> NINE_CATEGORIES = Set.of("FG%", "FT%", "3PTM", "PTS", "REB", "AST", "ST", "BLK", "TO");
    private static final Set<String> CATEGORY_TYPES = Set.of("head", "headone", "roto");

    /**
     * {@code season} is Yahoo's: the year the season starts. {@code warnings}: stats the league scores that the
     * values leave out, so they are approximate.
     */
    public record League(String name, int season, LeagueScoring scoring, List<String> warnings) {
    }

    /** {@code logoUrl}: the team's picture on Yahoo's image hosts, or null (the page then shows initials). */
    public record Team(String key, String name, String logoUrl) {
    }

    // the page may load images only from these (its Content-Security-Policy); other URLs are dropped
    private static final List<String> LOGO_HOSTS = List.of(
            "https://s.yimg.com/", "https://yahoofantasysports-res.cloudinary.com/");

    /** {@code positions}: the fantasy positions he can fill, e.g. PG and SG. */
    public record NamedPlayer(String name, Set<String> positions) {
    }

    public League league(String settingsJson) {
        JsonNode league = JsonMapper.shared().readTree(settingsJson).path("fantasy_content").path("league");
        JsonNode info = league.path(0);
        JsonNode settings = league.path(1).path("settings").path(0);

        Map<Integer, String> abbrById = new HashMap<>();
        List<String> categories = new ArrayList<>();
        for (JsonNode entry : settings.path("stat_categories").path("stats")) {
            JsonNode stat = entry.path("stat");
            abbrById.put(stat.path("stat_id").asInt(), stat.path("abbr").asString(""));
            if (!stat.path("is_only_display_stat").asString("0").equals("1")) {
                categories.add(stat.path("abbr").asString(""));
            }
        }

        List<String> warnings = new ArrayList<>();
        JsonNode modifiers = settings.path("stat_modifiers").path("stats");
        LeagueScoring scoring;
        // point values decide it, not scoring_type: new formats (High Score) keep working as long as they have them
        if (!modifiers.isEmpty()) {
            Map<PointStat, Double> pointValues = new EnumMap<>(PointStat.class);
            for (JsonNode entry : modifiers) {
                String abbr = abbrById.getOrDefault(entry.path("stat").path("stat_id").asInt(), "?");
                double value = Double.parseDouble(entry.path("stat").path("value").asString("0"));
                PointStat stat = POINT_STATS.get(abbr);
                if (stat == null) {
                    warnings.add("The league scores %s (%s points), which is not counted".formatted(abbr, value));
                } else {
                    pointValues.put(stat, value);
                }
            }
            scoring = new LeagueScoring.Points(pointValues);
        } else if (CATEGORY_TYPES.contains(info.path("scoring_type").asString(""))) {
            Set<String> differences = new LinkedHashSet<>(categories);
            differences.removeAll(NINE_CATEGORIES);
            NINE_CATEGORIES.stream().filter(category -> !categories.contains(category)).forEach(differences::add);
            if (!differences.isEmpty()) {
                warnings.add("The league's categories differ from the standard nine (%s); values use the nine"
                        .formatted(String.join(", ", differences)));
            }
            scoring = new LeagueScoring.Categories();
        } else {
            throw new IllegalArgumentException("Unsupported Yahoo scoring type \"%s\"; use typed names"
                    .formatted(info.path("scoring_type").asString("")));
        }
        return new League(info.path("name").asString(""), Integer.parseInt(info.path("season").asString("0")), scoring,
                List.copyOf(warnings));
    }

    public List<Team> teams(String teamsJson) {
        JsonNode teams = JsonMapper.shared().readTree(teamsJson).path("fantasy_content").path("league").path(1).path("teams");
        List<Team> result = new ArrayList<>();
        for (JsonNode team : list(teams, "team")) {
            JsonNode parts = team.path(0);
            String logo = part(parts, "team_logos").path(0).path("team_logo").path("url").asString("");
            result.add(new Team(part(parts, "team_key").asString(""), part(parts, "name").asString(""),
                    LOGO_HOSTS.stream().anyMatch(logo::startsWith) ? logo : null));
        }
        return result;
    }

    /** Players from a roster or a free agent page; an empty page (past the last free agent) gives none. */
    public List<NamedPlayer> players(String json) {
        List<NamedPlayer> players = new ArrayList<>();
        for (JsonNode player : list(JsonMapper.shared().readTree(json).findPath("players"), "player")) {
            JsonNode parts = player.path(0);
            String positions = part(parts, "display_position").asString("");
            players.add(new NamedPlayer(part(parts, "name").path("full").asString(""),
                    positions.isEmpty() ? Set.of() : Set.of(positions.split(","))));
        }
        return players;
    }

    // {"0": {"team": ...}, "1": {...}, "count": 2} -> the "team" of each entry; Yahoo sends [] when there are none
    private static List<JsonNode> list(JsonNode container, String field) {
        List<JsonNode> items = new ArrayList<>();
        int count = container.path("count").asInt(0);
        for (int i = 0; i < count; i++) {
            items.add(container.path(String.valueOf(i)).path(field));
        }
        return items;
    }

    // [{"team_key": ...}, {"name": ...}, [], ...] -> the value of the one-field object holding {@code field}
    private static JsonNode part(JsonNode parts, String field) {
        for (JsonNode part : parts) {
            if (part.has(field)) {
                return part.path(field);
            }
        }
        return parts.path(field); // a MissingNode: reads as the default
    }
}
