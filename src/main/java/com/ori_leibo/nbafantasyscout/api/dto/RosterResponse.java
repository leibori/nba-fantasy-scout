package com.ori_leibo.nbafantasyscout.api.dto;

import com.ori_leibo.nbafantasyscout.roster.RosterService.RosterAdvice;
import java.util.List;

/**
 * Advice per roster name (in the order sent), free agents worth picking up now, names that matched nobody, and
 * league stats the values leave out.
 */
public record RosterResponse(List<PlayerAdviceResponse> roster, List<PlayerFormResponse> pickUpNow,
                             List<String> unmatched, List<String> warnings) {

    public static RosterResponse from(RosterAdvice advice, List<String> warnings) {
        return new RosterResponse(
                advice.roster().stream().map(PlayerAdviceResponse::from).toList(),
                advice.pickUpNow().stream().map(PlayerFormResponse::from).toList(),
                advice.unmatched(),
                warnings);
    }
}
