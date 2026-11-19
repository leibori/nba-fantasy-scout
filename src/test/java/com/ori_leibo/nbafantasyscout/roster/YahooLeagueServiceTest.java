package com.ori_leibo.nbafantasyscout.roster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ori_leibo.nbafantasyscout.client.YahooClient;
import com.ori_leibo.nbafantasyscout.client.YahooMapper;
import com.ori_leibo.nbafantasyscout.domain.LeagueScoring;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class YahooLeagueServiceTest {

    private static final long LEAGUE_ID = 246889;
    private static final int SEASON = 2027; // ours: 2026-27, which Yahoo calls 2026
    private static final List<YahooMapper.Team> TEAMS = List.of(
            new YahooMapper.Team("478.l.246889.t.1", "Ramat-Gan Eshet Chayil"),
            new YahooMapper.Team("478.l.246889.t.2", "Givaat Shmuel Rabbis"));

    @Mock
    private YahooClient yahoo;

    private YahooLeagueService service() {
        return new YahooLeagueService(yahoo, SEASON);
    }

    private static YahooMapper.League league(int yahooSeason) {
        return new YahooMapper.League("Test", yahooSeason, new LeagueScoring.Categories(), List.of("a warning"));
    }

    @Test
    void readsTheTeamByNameIgnoringCaseAndSpaces() {
        when(yahoo.league(LEAGUE_ID)).thenReturn(league(2026));
        when(yahoo.teams(LEAGUE_ID)).thenReturn(TEAMS);
        when(yahoo.roster("478.l.246889.t.2")).thenReturn(List.of("Cooper Flagg"));
        when(yahoo.freeAgents(LEAGUE_ID)).thenReturn(List.of("Tari Eason"));

        YahooLeagueService.LeagueTeam team = service().read(LEAGUE_ID, "  givaat shmuel RABBIS ");

        assertEquals(new YahooLeagueService.LeagueTeam(new LeagueScoring.Categories(), List.of("Cooper Flagg"),
                List.of("Tari Eason"), List.of("a warning")), team);
    }

    @Test
    void lastSeasonsLeagueIsRefusedBeforeReadingAnyTeam() {
        when(yahoo.league(LEAGUE_ID)).thenReturn(league(2025));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service().read(LEAGUE_ID, "Givaat Shmuel Rabbis"));

        assertTrue(e.getMessage().contains("2025-2026 season, not 2026-2027"), e.getMessage());
        verify(yahoo, never()).teams(LEAGUE_ID);
    }

    @Test
    void anUnknownTeamListsTheLeaguesTeams() {
        when(yahoo.league(LEAGUE_ID)).thenReturn(league(2026));
        when(yahoo.teams(LEAGUE_ID)).thenReturn(TEAMS);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service().read(LEAGUE_ID, "Givaat Shmuel"));

        assertTrue(e.getMessage().endsWith("its teams are: Ramat-Gan Eshet Chayil, Givaat Shmuel Rabbis"), e.getMessage());
    }
}
