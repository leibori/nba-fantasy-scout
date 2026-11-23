package com.ori_leibo.nbafantasyscout.api.dto;

import com.ori_leibo.nbafantasyscout.recommend.TrendService.CategoryLeader;

/**
 * A free agent's standing in one category: {@code perGame} his season average (a fraction for FG%/FT%, e.g. 0.52),
 * {@code attempts} per game for those two, {@code score} in league standard deviations (higher is better).
 */
public record CategoryLeaderResponse(PlayerFormResponse player, double perGame, Double attempts, double score) {

    public static CategoryLeaderResponse from(CategoryLeader leader) {
        return new CategoryLeaderResponse(PlayerFormResponse.from(leader.form()), StatLineResponse.round(leader.perGame()),
                leader.attempts() == null ? null : StatLineResponse.round(leader.attempts()),
                StatLineResponse.round(leader.score()));
    }
}
