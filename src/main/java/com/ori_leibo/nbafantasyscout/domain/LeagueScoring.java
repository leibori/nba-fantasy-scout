package com.ori_leibo.nbafantasyscout.domain;

import java.util.Map;

/** How a fantasy league turns stats into wins: nine categories won one by one, or a single points total. */
public sealed interface LeagueScoring {

    /** The format's name, for config and {@code ?scoring=}; a {@link Points} league also needs its point values. */
    enum Format { CATEGORIES, POINTS }

    record Categories() implements LeagueScoring {
    }

    /** Points per unit of each stat, e.g. REB 1.2, TO -1 (a negative value is a penalty). */
    record Points(Map<PointStat, Double> pointValues) implements LeagueScoring {

        public Points {
            pointValues = Map.copyOf(pointValues);
        }

        public double fantasyPoints(StatLine line) {
            return pointValues.entrySet().stream()
                    .mapToDouble(e -> e.getValue() * e.getKey().valueOf(line))
                    .sum();
        }
    }
}
