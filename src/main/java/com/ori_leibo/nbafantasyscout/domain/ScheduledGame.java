package com.ori_leibo.nbafantasyscout.domain;

/** One game on a day's schedule; {@code started}: under way or over, so it is no longer a game left to play. */
public record ScheduledGame(String homeTeam, String awayTeam, boolean started) {
}
