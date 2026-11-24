package com.ori_leibo.nbafantasyscout.roster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ori_leibo.nbafantasyscout.domain.LeagueScoring;
import com.ori_leibo.nbafantasyscout.domain.Player;
import com.ori_leibo.nbafantasyscout.domain.PointStat;
import com.ori_leibo.nbafantasyscout.recommend.CrowdSignal;
import com.ori_leibo.nbafantasyscout.recommend.TrendService;
import com.ori_leibo.nbafantasyscout.recommend.TrendService.PlayerForm;
import com.ori_leibo.nbafantasyscout.roster.RosterService.PlayerAdvice;
import com.ori_leibo.nbafantasyscout.roster.RosterService.RosterAdvice;
import com.ori_leibo.nbafantasyscout.trend.FormClassifier.Form;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// the forms are mocked; matching and the rule chain are the real pure-Java classes
@ExtendWith(MockitoExtension.class)
class RosterServiceTest {

    private static final LeagueScoring POINTS = new LeagueScoring.Points(Map.of(PointStat.PTS, 1.0));

    private static PlayerForm form(long id, String name, String status, double value, Form form) {
        return new PlayerForm(new Player(id, name, "LAL", "G", status, 3), 10, null, null, value, form, null,
                CrowdSignal.NONE);
    }

    private static final PlayerForm FLAGG = form(1, "Cooper Flagg", null, 2.1, Form.GOOD);
    private static final PlayerForm REED = form(2, "Paul Reed", null, 0.1, Form.NEUTRAL);
    private static final PlayerForm EDEY = form(3, "Zach Edey", null, -0.2, Form.NEUTRAL);
    private static final PlayerForm WAIVER_GUARD = form(4, "Waiver Guard", null, 0.9, Form.NEUTRAL);
    private static final PlayerForm SECOND_OPTION = form(5, "Second Option", null, 0.7, Form.NEUTRAL);
    private static final PlayerForm HOT_ROOKIE = form(6, "Hot Rookie", null, 0.4, Form.HOT);
    private static final PlayerForm INJURED_BIG = form(7, "Injured Big", "Out", 3.0, Form.GOOD);

    @Mock
    private TrendService trends;
    private RosterService service;

    @BeforeEach
    void setUp() {
        // drop gap 1.5 in 9-cat, 0.5 in points; suggest 3 pickups, 3 streams, 10 per category
        service = new RosterService(trends, new PlayerMatcher(0.85), new RosterRules(RosterRules.DEFAULT_ORDER),
                1.5, 0.5, 3, 3, 10);
        // lenient: the streams test asks for another format
        lenient().when(trends.formOfAllPlayers(POINTS)).thenReturn(
                List.of(FLAGG, REED, EDEY, WAIVER_GUARD, SECOND_OPTION, HOT_ROOKIE, INJURED_BIG));
    }

    @Test
    void withoutAFreeAgentListEveryOtherPlayerIsAvailableAndEachReplacementIsSuggestedOnce() {
        RosterAdvice advice = service.advise(List.of("cooper flagg", "Paul Reed", "Zach Edey"), List.of(), Map.of(), Map.of(), false, POINTS);

        // input order kept; the weakest (Edey) gets the best free agent, Reed the next one
        assertEquals(List.of("cooper flagg", "Paul Reed", "Zach Edey"), advice.roster().stream().map(PlayerAdvice::name).toList());
        assertInstanceOf(Recommendation.Keep.class, advice.roster().get(0).recommendation());
        assertEquals(SECOND_OPTION, assertInstanceOf(Recommendation.Drop.class, advice.roster().get(1).recommendation()).replacement());
        assertEquals(WAIVER_GUARD, assertInstanceOf(Recommendation.Drop.class, advice.roster().get(2).recommendation()).replacement());

        // rising first, then value; the injured free agent is never suggested
        assertEquals(List.of(HOT_ROOKIE, WAIVER_GUARD, SECOND_OPTION), advice.pickUpNow());
        assertTrue(advice.unmatched().isEmpty());
        // the category lists draw from the same pool: free agents only, never the injured one
        verify(trends).categoryLeaders(List.of(WAIVER_GUARD, SECOND_OPTION, HOT_ROOKIE), 10);
    }

    @Test
    void onlyTheGivenFreeAgentsCountAndWeakMatchesAreFlaggedOrLeftOut() {
        RosterAdvice advice = service.advise(List.of("Zak Edy", "Paul Reed", " "),
                List.of("Hot Rookie", "Nobody At All"), Map.of(), Map.of(), false, POINTS);

        // a weak roster match is still the user's player, so it is kept and flagged
        PlayerAdvice edey = advice.roster().get(0);
        assertEquals(EDEY, edey.form());
        assertTrue(edey.unsure());
        assertEquals(HOT_ROOKIE, assertInstanceOf(Recommendation.Drop.class, edey.recommendation()).replacement());
        // the only free agent went to Edey, so Reed has nobody left to be swapped for
        assertFalse(advice.roster().get(1).unsure());
        assertInstanceOf(Recommendation.Watch.class, advice.roster().get(1).recommendation());

        // a blank name matches nobody; a weak free agent match is never suggested
        assertEquals(List.of(" ", "Nobody At All"), advice.unmatched());
        assertEquals(List.of(HOT_ROOKIE), advice.pickUpNow());
    }

    @Test
    void aLeagueSaysFromWhichDayEachFreeAgentCanPlay() {
        LocalDate today = LocalDate.of(2026, 11, 24);
        LocalDate clearsWaivers = LocalDate.of(2026, 11, 25);
        when(trends.gamesLeftFrom("LAL", today)).thenReturn(3);           // tonight's game counts
        when(trends.gamesLeftFrom("LAL", clearsWaivers)).thenReturn(2);   // a waiver claim misses it

        RosterAdvice advice = service.advise(List.of("Paul Reed"), List.of("Waiver Guard", "Second Option"), Map.of(),
                Map.of("Waiver Guard", today, "Second Option", clearsWaivers), false, POINTS);

        assertEquals(Map.of("Waiver Guard", 3, "Second Option", 2), advice.pickUpNow().stream()
                .collect(Collectors.toMap(form -> form.player().name(), PlayerForm::gamesLeftThisWeek)));
    }

    @Test
    void streamsRankFreeAgentsByValueTimesGamesLeftThisWeek() {
        LeagueScoring categories = new LeagueScoring.Categories();
        PlayerForm fourGames = thisWeek(11, "Four Games", 0.5, 4);          // 2.0
        PlayerForm twoGames = thisWeek(12, "Two Games", 0.9, 2);            // 1.8, better per game but fewer games
        PlayerForm doneForTheWeek = thisWeek(13, "Done For The Week", 2.0, 0);
        PlayerForm belowAverage = thisWeek(14, "Below Average", -0.3, 4);   // no stream at any number of games
        when(trends.formOfAllPlayers(categories)).thenReturn(List.of(FLAGG, fourGames, twoGames, doneForTheWeek, belowAverage));

        RosterAdvice advice = service.advise(List.of("Cooper Flagg"), List.of(), Map.of(), Map.of(), false, categories);

        assertEquals(List.of(fourGames, twoGames), advice.streams());
    }

    private static PlayerForm thisWeek(long id, String name, double value, int gamesLeft) {
        return new PlayerForm(new Player(id, name, "BOS", "F", null, 3), 10, null, null, value, Form.NEUTRAL, null,
                CrowdSignal.NONE, LocalDate.of(2026, 11, 22), 0, gamesLeft);
    }

    @Test
    void samePositionReplacesOnlyWithAFreeAgentSharingAPosition() {
        Map<String, Set<String>> positions = Map.of("Zach Edey", Set.of("C"),
                "Waiver Guard", Set.of("PG", "SG"), "Second Option", Set.of("PF", "C"));

        RosterAdvice any = service.advise(List.of("Zach Edey"), List.of("Waiver Guard", "Second Option"), positions,
                Map.of(), false, POINTS);
        RosterAdvice same = service.advise(List.of("Zach Edey"), List.of("Waiver Guard", "Second Option"), positions,
                Map.of(), true, POINTS);

        assertEquals(WAIVER_GUARD, assertInstanceOf(Recommendation.Drop.class, any.roster().get(0).recommendation()).replacement());
        // the guard is worth more, but only the PF/C can fill Edey's C spot
        assertEquals(SECOND_OPTION, assertInstanceOf(Recommendation.Drop.class, same.roster().get(0).recommendation()).replacement());
        // pick up now has no dropped player to match, so positions don't filter it
        assertEquals(List.of(WAIVER_GUARD, SECOND_OPTION), same.pickUpNow());
    }
}
