package com.ori_leibo.nbafantasyscout.recommend;

import com.ori_leibo.nbafantasyscout.domain.Player;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;

/**
 * Each team's game dates this season, read from its current players' game logs. ESPN's logs hold only the games a
 * player played, so a game he missed (injured, or not used) is simply absent; comparing his last game with his
 * team's dates shows how many he has missed. A date counts for a team only when at least {@value #MIN_PLAYERS} of
 * its current players played that day: a player traded in brings his old team's dates along, and those must not
 * look like games of his new team.
 */
public final class TeamGames {

    static final int MIN_PLAYERS = 3;

    private final Map<String, NavigableSet<LocalDate>> datesByTeam = new HashMap<>();

    /** {@code datesByPlayer}: the days each player played this season; players without a team are ignored. */
    public TeamGames(Map<Player, ? extends Collection<LocalDate>> datesByPlayer) {
        Map<String, Map<LocalDate, Integer>> playersByTeamAndDate = new HashMap<>();
        datesByPlayer.forEach((player, dates) -> {
            if (player.team() == null) {
                return;
            }
            Map<LocalDate, Integer> counts = playersByTeamAndDate.computeIfAbsent(player.team(), team -> new HashMap<>());
            new HashSet<>(dates).forEach(date -> counts.merge(date, 1, Integer::sum));
        });
        playersByTeamAndDate.forEach((team, counts) -> {
            NavigableSet<LocalDate> dates = new TreeSet<>();
            counts.forEach((date, players) -> {
                if (players >= MIN_PLAYERS) {
                    dates.add(date);
                }
            });
            datesByTeam.put(team, dates);
        });
    }

    /** How many of the team's games came after {@code lastGame}; 0 without a team or a last game. */
    public int missedSince(String team, LocalDate lastGame) {
        if (team == null || lastGame == null) {
            return 0;
        }
        NavigableSet<LocalDate> dates = datesByTeam.get(team);
        return dates == null ? 0 : dates.tailSet(lastGame, false).size();
    }
}
