package com.ori_leibo.nbafantasyscout.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class StatLineTest {

    private static GameLog game(int minutes, int fgm, int fga, int ftm, int fta, int pts, int tov) {
        return new GameLog(LocalDate.of(2026, 1, 1), minutes, fgm, fga, 0, ftm, fta, 0, 0, 0, 0, tov, pts);
    }

    @Test
    void averagesCountingStatsPerGame() {
        StatLine line = StatLine.average(List.of(game(30, 0, 0, 0, 0, 20, 2), game(40, 0, 0, 0, 0, 30, 4)));

        assertEquals(35.0, line.minutes());
        assertEquals(25.0, line.pts());
        assertEquals(3.0, line.tov());
        assertEquals(25.0, StatCategory.PTS.valueOf(line));
    }

    @Test
    void shootingPercentagesAreWeightedByVolume() {
        StatLine line = StatLine.average(List.of(game(30, 8, 10, 1, 4, 0, 0), game(30, 1, 1, 3, 4, 0, 0)));

        assertEquals(9.0 / 11, line.fgPct(), 1e-9);       // not (80% + 100%) / 2
        assertEquals(4.0 / 8, StatCategory.FT_PCT.valueOf(line), 1e-9);
    }

    @Test
    void zeroAttemptsGiveZeroPercentAndEmptyListIsRejected() {
        StatLine line = StatLine.average(List.of(game(10, 0, 0, 0, 0, 0, 0)));

        assertEquals(0.0, line.fgPct());
        assertEquals(0.0, line.ftPct());
        assertThrows(IllegalArgumentException.class, () -> StatLine.average(List.of()));
    }
}
