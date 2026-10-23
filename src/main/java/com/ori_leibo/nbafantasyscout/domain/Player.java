package com.ori_leibo.nbafantasyscout.domain;

/** An NBA player as listed on a team roster. {@code status} is the injury status, null when healthy. */
public record Player(long id, String name, String team, String position, String status) {
}
