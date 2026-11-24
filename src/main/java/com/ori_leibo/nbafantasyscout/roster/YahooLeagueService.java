package com.ori_leibo.nbafantasyscout.roster;

import com.ori_leibo.nbafantasyscout.client.YahooClient;
import com.ori_leibo.nbafantasyscout.client.YahooMapper;
import com.ori_leibo.nbafantasyscout.domain.GameLog;
import com.ori_leibo.nbafantasyscout.domain.LeagueScoring;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    /**
     * {@code positions}: Yahoo's positions per roster and free agent name; {@code playsFrom}: per name, the first day
     * he can play for the user (the league's edit date, or the day a free agent clears waivers); {@code warnings}:
     * stats the league scores that the values leave out.
     */
    public record LeagueTeam(LeagueScoring scoring, List<String> roster, List<String> freeAgents,
                             Map<String, Set<String>> positions, Map<String, LocalDate> playsFrom, List<String> warnings) {
    }

    /** Throws {@link IllegalArgumentException} with a message for the user when the league or team doesn't fit. */
    public LeagueTeam read(long leagueId, String teamName) {
        YahooMapper.League league = currentLeague(leagueId);
        List<YahooMapper.Team> teams = yahoo.teams(leagueId);
        YahooMapper.Team team = teams.stream()
                .filter(candidate -> candidate.name().equalsIgnoreCase(teamName.strip()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No team \"%s\" in Yahoo league %d; its teams are: %s"
                        .formatted(teamName, leagueId, String.join(", ", teams.stream().map(YahooMapper.Team::name).toList()))));
        List<YahooMapper.NamedPlayer> roster = yahoo.roster(team.key());
        List<YahooMapper.NamedPlayer> freeAgents = yahoo.freeAgents(leagueId);
        // two players with one name in a league is rare; the first one's positions are kept
        Map<String, Set<String>> positions = Stream.concat(roster.stream(), freeAgents.stream())
                .collect(Collectors.toMap(YahooMapper.NamedPlayer::name, YahooMapper.NamedPlayer::positions, (a, b) -> a));
        Map<String, LocalDate> playsFrom = new HashMap<>();
        if (league.editDate() != null) {
            roster.forEach(player -> playsFrom.put(player.name(), league.editDate()));
        }
        freeAgents.forEach(player -> {
            LocalDate from = player.waiverDate() != null ? player.waiverDate() : league.editDate();
            if (from != null) {
                playsFrom.putIfAbsent(player.name(), from);
            }
        });
        return new LeagueTeam(league.scoring(), names(roster), names(freeAgents), positions, Map.copyOf(playsFrom),
                league.warnings());
    }

    /** The league's teams, for the user to pick theirs; the same season check as {@link #read}. */
    public List<YahooMapper.Team> teams(long leagueId) {
        currentLeague(leagueId);
        return yahoo.teams(leagueId);
    }

    private YahooMapper.League currentLeague(long leagueId) {
        YahooMapper.League league = yahoo.league(leagueId);
        int season = GameLog.seasonOf(LocalDate.now(clock));
        // Yahoo names a season by the year it starts, we by the year it ends
        if (league.season() != season - 1) {
            throw new IllegalArgumentException(("Yahoo league %d is from the %d-%d season, not %d-%d; a league id "
                    + "belongs to one season, use this season's id").formatted(
                    leagueId, league.season(), league.season() + 1, season - 1, season));
        }
        return league;
    }

    private static List<String> names(List<YahooMapper.NamedPlayer> players) {
        return players.stream().map(YahooMapper.NamedPlayer::name).toList();
    }
}
