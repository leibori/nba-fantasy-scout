package com.ori_leibo.nbafantasyscout.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * A public Yahoo league by its id (the number in the league's URL) and the user's team name in it.
 * {@code samePosition}: replace a dropped player only with a free agent of his position (default false).
 */
public record YahooRosterRequest(@NotNull Long leagueId, @NotBlank String teamName, Boolean samePosition) {

    public YahooRosterRequest {
        // a missing field is null, and Jackson refuses null for a primitive
        samePosition = Boolean.TRUE.equals(samePosition);
    }
}
