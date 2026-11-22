package com.ori_leibo.nbafantasyscout.api.dto;

import com.ori_leibo.nbafantasyscout.recommend.WeekSchedule;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/** This week's games per team, most games left first; {@code from}: the first day counted as left (tomorrow). */
public record WeekScheduleResponse(LocalDate start, LocalDate end, LocalDate from, List<TeamGames> teams) {

    public record TeamGames(String team, int games, int left) {
    }

    public static WeekScheduleResponse from(WeekSchedule.Week week) {
        return new WeekScheduleResponse(week.start(), week.end(), week.from(), week.teams().entrySet().stream()
                .map(entry -> new TeamGames(entry.getKey(), entry.getValue().games(), entry.getValue().left()))
                .sorted(Comparator.comparingInt(TeamGames::left).reversed().thenComparing(TeamGames::team))
                .toList());
    }
}
