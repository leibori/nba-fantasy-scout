package com.ori_leibo.nbafantasyscout.domain;

import java.time.Instant;

/** How many Sleeper leagues added or dropped a player in the look-back window, as seen at {@code fetchedAt}. */
public record TrendingSnapshot(long playerId, Type type, int count, Instant fetchedAt) {

    public enum Type { ADD, DROP }
}
