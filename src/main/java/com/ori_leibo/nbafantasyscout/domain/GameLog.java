package com.ori_leibo.nbafantasyscout.domain;

import java.time.LocalDate;

/** One player's raw box score for one game. */
public record GameLog(
        LocalDate gameDate,
        int minutes,
        int fgm, int fga,
        int fg3m,
        int ftm, int fta,
        int reb, int ast, int stl, int blk, int tov, int pts) {
}
