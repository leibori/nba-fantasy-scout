package com.ori_leibo.nbafantasyscout.domain;

/**
 * An NBA player as listed on a team roster. {@code status} is the injury status, null when healthy;
 * {@code experienceYears} is ESPN's count of earlier NBA seasons (0 = rookie), null when unknown.
 */
public record Player(long id, String name, String team, String position, String status, Integer experienceYears) {
}
