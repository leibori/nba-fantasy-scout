package com.ori_leibo.nbafantasyscout.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** A public Yahoo league by its id (the number in the league's URL) and the user's team name in it. */
public record YahooRosterRequest(@NotNull Long leagueId, @NotBlank String teamName) {
}
