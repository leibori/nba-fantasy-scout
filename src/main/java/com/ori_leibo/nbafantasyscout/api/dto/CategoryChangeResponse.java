package com.ori_leibo.nbafantasyscout.api.dto;

import com.ori_leibo.nbafantasyscout.recommend.RecommendationService.CategoryChange;

/** {@code change} is recent minus baseline per game (fewer turnovers counts as positive); percentages as fractions. */
public record CategoryChangeResponse(String category, double change, PlayerFormResponse player) {

    public static CategoryChangeResponse from(String category, CategoryChange change) {
        return new CategoryChangeResponse(category, StatLineResponse.round(change.change()),
                PlayerFormResponse.from(change.form()));
    }
}
