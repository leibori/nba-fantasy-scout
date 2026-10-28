package com.ori_leibo.nbafantasyscout.api.dto;

import com.ori_leibo.nbafantasyscout.domain.StatLine;

/** Per-game averages in the 9 Yahoo categories plus minutes, rounded for reading. */
public record StatLineResponse(double minutes, double points, double rebounds, double assists, double steals,
                               double blocks, double threes, double turnovers, double fieldGoalPct,
                               double freeThrowPct) {

    /** Null in, null out: a player without a trend has no recent or baseline line. */
    public static StatLineResponse from(StatLine line) {
        if (line == null) {
            return null;
        }
        return new StatLineResponse(round(line.minutes()), round(line.points()), round(line.rebounds()),
                round(line.assists()), round(line.steals()), round(line.blocks()), round(line.threesMade()),
                round(line.turnovers()), round(line.fieldGoalPct()), round(line.freeThrowPct()));
    }

    static double round(double value) {
        return Math.round(value * 1000) / 1000.0;
    }
}
