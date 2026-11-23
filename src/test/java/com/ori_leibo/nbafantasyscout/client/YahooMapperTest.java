package com.ori_leibo.nbafantasyscout.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ori_leibo.nbafantasyscout.domain.LeagueScoring;
import com.ori_leibo.nbafantasyscout.domain.PointStat;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.Map;
import org.junit.jupiter.api.Test;

// the files are real responses from a public league (manager details removed); the inline JSON covers formats
// we have no real league for
class YahooMapperTest {

    private final YahooMapper mapper = new YahooMapper();

    @Test
    void readsAPointsLeagueWithItsPointValues() {
        YahooMapper.League league = mapper.league(fixture("settings.json"));

        assertEquals(2026, league.season());
        assertEquals(new LeagueScoring.Points(Map.of(PointStat.PTS, 1.0, PointStat.REB, 1.2, PointStat.AST, 1.5,
                PointStat.STL, 3.0, PointStat.BLK, 3.0, PointStat.TO, -1.0)), league.scoring());
        assertTrue(league.warnings().isEmpty());
    }

    @Test
    void readsTeamsRosterAndFreeAgentsWithTheirPositions() {
        List<YahooMapper.Team> teams = mapper.teams(fixture("teams.json"));
        assertEquals(10, teams.size());
        assertTrue(teams.contains(new YahooMapper.Team("478.l.246889.t.2", "Givaat Shmuel Rabbis",
                "https://yahoofantasysports-res.cloudinary.com/image/upload/t_s192sq/fantasy-logos/"
                        + "1ce8b73edc982c63f4afb5cb149be5a20c63e6af54044a1f06febb1301e8b550.jpg")));
        assertEquals("https://s.yimg.com/ep/cx/blendr/v2/image-witch-png_1721174369206.png", teams.get(2).logoUrl());

        List<YahooMapper.NamedPlayer> roster = mapper.players(fixture("roster.json"));
        assertEquals(13, roster.size());
        assertTrue(roster.stream().map(YahooMapper.NamedPlayer::name).toList()
                .containsAll(List.of("Cooper Flagg", "Zach Edey", "P.J. Washington")));
        assertTrue(roster.contains(new YahooMapper.NamedPlayer("Cooper Flagg", Set.of("SG", "SF", "PF"))));

        List<YahooMapper.NamedPlayer> freeAgents = mapper.players(fixture("free-agents.json"));
        assertEquals(25, freeAgents.size());
        assertEquals("Tari Eason", freeAgents.get(0).name());
    }

    @Test
    void aStatWeCannotComputeIsLeftOutWithAWarning() {
        YahooMapper.League league = mapper.league(settings("headpoint",
                stat(12, "PTS", null) + "," + stat(27, "DD", null), modifier(12, "1") + "," + modifier(27, "5")));

        assertEquals(new LeagueScoring.Points(Map.of(PointStat.PTS, 1.0)), league.scoring());
        assertEquals(List.of("The league scores DD (5.0 points), which is not counted"), league.warnings());
    }

    @Test
    void aCategoriesLeagueIsScoredOnTheNineAndADifferentSetIsFlagged() {
        String eightWithoutTurnovers = String.join(",", stat(9004003, "FGM/A", "1"), stat(5, "FG%", null),
                stat(8, "FT%", null), stat(10, "3PTM", null), stat(12, "PTS", null), stat(15, "REB", null),
                stat(16, "AST", null), stat(17, "ST", null), stat(18, "BLK", null));

        YahooMapper.League league = mapper.league(settings("head", eightWithoutTurnovers, ""));

        assertEquals(new LeagueScoring.Categories(), league.scoring());
        // the display-only FGM/A is not a category; the missing TO is
        assertEquals(List.of("The league's categories differ from the standard nine (TO); values use the nine"),
                league.warnings());
    }

    @Test
    void anUnknownFormatIsRefusedAndAnEmptyPageHasNoPlayers() {
        assertThrows(IllegalArgumentException.class, () -> mapper.league(settings("survivor", stat(12, "PTS", null), "")));
        assertEquals(List.of(), mapper.players("{\"fantasy_content\": {\"league\": [{}, {\"players\": []}]}}"));
    }

    private static String settings(String scoringType, String stats, String modifiers) {
        return """
                {"fantasy_content": {"league": [
                  {"name": "Test", "season": "2026", "scoring_type": "%s"},
                  {"settings": [{"stat_categories": {"stats": [%s]}, "stat_modifiers": {"stats": [%s]}}]}]}}"""
                .formatted(scoringType, stats, modifiers);
    }

    private static String stat(int id, String abbr, String displayOnly) {
        return displayOnly == null
                ? "{\"stat\": {\"stat_id\": %d, \"abbr\": \"%s\"}}".formatted(id, abbr)
                : "{\"stat\": {\"stat_id\": %d, \"abbr\": \"%s\", \"is_only_display_stat\": \"%s\"}}".formatted(id, abbr, displayOnly);
    }

    private static String modifier(int id, String value) {
        return "{\"stat\": {\"stat_id\": %d, \"value\": \"%s\"}}".formatted(id, value);
    }

    static String fixture(String name) {
        try (InputStream in = YahooMapperTest.class.getResourceAsStream("/yahoo/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
