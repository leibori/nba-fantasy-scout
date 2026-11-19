package com.ori_leibo.nbafantasyscout.roster;

import com.ori_leibo.nbafantasyscout.client.YahooClient;
import com.ori_leibo.nbafantasyscout.client.YahooMapper;
import com.ori_leibo.nbafantasyscout.domain.GameLog;
import com.ori_leibo.nbafantasyscout.domain.LeagueScoring;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * A user's team in a public Yahoo league, checked before use: the league must be this season's (a Yahoo league id
 * belongs to one season) and the team must exist in it.
 */
public class YahooLeagueService {

    private final YahooClient yahoo;
    private final Clock clock;

    public YahooLeagueService(YahooClient yahoo, Clock clock) {
        this.yahoo = yahoo;
        this.clock = clock;
    }

    /** {@code warnings}: stats the league scores that the values leave out. */
    public record LeagueTeam(LeagueScoring scoring, List<String> roster, List<String> freeAgents, List<String> warnings) {
    }

    /** Throws {@link IllegalArgumentException} with a message for the user when the league or team doesn't fit. */
    public LeagueTeam read(long leagueId, String teamName) {
        YahooMapper.League league = yahoo.league(leagueId);
        int season = GameLog.seasonOf(LocalDate.now(clock));
        // Yahoo names a season by the year it starts, we by the year it ends
        if (league.season() != season - 1) {
            throw new IllegalArgumentException(("Yahoo league %d is from the %d-%d season, not %d-%d; a league id "
                    + "belongs to one season, use this season's id").formatted(
                    leagueId, league.season(), league.season() + 1, season - 1, season));
        }
        List<YahooMapper.Team> teams = yahoo.teams(leagueId);
        YahooMapper.Team team = teams.stream()
                .filter(candidate -> candidate.name().equalsIgnoreCase(teamName.strip()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No team \"%s\" in Yahoo league %d; its teams are: %s"
                        .formatted(teamName, leagueId, String.join(", ", teams.stream().map(YahooMapper.Team::name).toList()))));
        return new LeagueTeam(league.scoring(), yahoo.roster(team.key()), yahoo.freeAgents(leagueId), league.warnings());
    }
}
