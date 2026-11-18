package com.ori_leibo.nbafantasyscout.api.dto;

import com.ori_leibo.nbafantasyscout.roster.RosterService.RosterAdvice;
import java.util.List;

/** Advice per roster name (in the order sent), free agents worth picking up now, and names that matched nobody. */
public record RosterResponse(List<PlayerAdviceResponse> roster, List<PlayerFormResponse> pickUpNow,
                             List<String> unmatched) {

    public static RosterResponse from(RosterAdvice advice) {
        return new RosterResponse(
                advice.roster().stream().map(PlayerAdviceResponse::from).toList(),
                advice.pickUpNow().stream().map(PlayerFormResponse::from).toList(),
                advice.unmatched());
    }
}
