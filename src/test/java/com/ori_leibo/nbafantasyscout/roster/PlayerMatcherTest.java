package com.ori_leibo.nbafantasyscout.roster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ori_leibo.nbafantasyscout.domain.Player;
import com.ori_leibo.nbafantasyscout.roster.PlayerMatcher.Match;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlayerMatcherTest {

    private static final Player JOKIC = player(1, "Nikola Jokić");
    private static final Player DONCIC = player(2, "Luka Dončić");
    private static final Player JACKSON = player(3, "Jaren Jackson Jr.");
    private static final Player SGA = player(4, "Shai Gilgeous-Alexander");
    private static final Player JALEN = player(5, "Jalen Williams");
    private static final Player JAYLIN = player(6, "Jaylin Williams");
    private static final Player BRUNSON = player(7, "Jalen Brunson");
    private static final List<Player> PLAYERS = List.of(JOKIC, DONCIC, JACKSON, SGA, JALEN, JAYLIN, BRUNSON);

    private final PlayerMatcher matcher = new PlayerMatcher(0.8);

    @Test
    void ignoresCaseAccentsSuffixesAndPunctuation() {
        assertExact("nikola jokic", JOKIC);
        assertExact("Jaren Jackson", JACKSON);
        assertExact("Shai Gilgeous Alexander", SGA);
    }

    @Test
    void toleratesATypo() {
        Match match = matcher.match("Luka Donic", PLAYERS).orElseThrow();

        assertEquals(DONCIC, match.player());
        assertEquals(1 - 1.0 / 11, match.confidence(), 1e-9);
        assertFalse(match.unsure());
    }

    @Test
    void matchesAFirstInitial() {
        assertExact("J. Brunson", BRUNSON);
    }

    @Test
    void flagsAnInitialThatFitsTwoPlayers() {
        assertTrue(matcher.match("J. Williams", PLAYERS).orElseThrow().unsure());
    }

    @Test
    void flagsAWeakMatch() {
        assertTrue(matcher.match("Michael Jordan", PLAYERS).orElseThrow().unsure());
    }

    @Test
    void returnsEmptyForBlankNameOrNoPlayers() {
        assertTrue(matcher.match(" . ", PLAYERS).isEmpty());
        assertTrue(matcher.match("Nikola Jokic", List.of()).isEmpty());
    }

    @Test
    void levenshteinCountsEdits() {
        assertEquals(3, PlayerMatcher.levenshtein("kitten", "sitting"));
        assertEquals(4, PlayerMatcher.levenshtein("", "abcd"));
    }

    private void assertExact(String name, Player expected) {
        Match match = matcher.match(name, PLAYERS).orElseThrow();
        assertEquals(expected, match.player());
        assertEquals(1.0, match.confidence());
        assertFalse(match.unsure());
    }

    private static Player player(long id, String name) {
        return new Player(id, name, "TST", "G", null);
    }
}
