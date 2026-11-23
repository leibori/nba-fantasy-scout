package com.ori_leibo.nbafantasyscout.api.dto;

import com.ori_leibo.nbafantasyscout.domain.StatCategory;
import com.ori_leibo.nbafantasyscout.roster.RosterService.RosterAdvice;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Advice per roster name (in the order sent), free agents worth picking up now and this week, the best free agents
 * per category (keyed PTS, REB, BLK, FG_PCT...), names that matched nobody, and league stats the values leave out.
 */
public record RosterResponse(List<PlayerAdviceResponse> roster, List<PlayerFormResponse> pickUpNow,
                             List<PlayerFormResponse> streams, Map<StatCategory, List<CategoryLeaderResponse>> byCategory,
                             List<String> unmatched, List<String> warnings) {

    public static RosterResponse from(RosterAdvice advice, List<String> warnings) {
        return new RosterResponse(
                advice.roster().stream().map(PlayerAdviceResponse::from).toList(),
                advice.pickUpNow().stream().map(PlayerFormResponse::from).toList(),
                advice.streams().stream().map(PlayerFormResponse::from).toList(),
                byCategory(advice),
                advice.unmatched(),
                warnings);
    }

    // EnumMap keeps the categories in their Yahoo order in the JSON
    private static Map<StatCategory, List<CategoryLeaderResponse>> byCategory(RosterAdvice advice) {
        Map<StatCategory, List<CategoryLeaderResponse>> byCategory = new EnumMap<>(StatCategory.class);
        advice.byCategory().forEach((category, leaders) ->
                byCategory.put(category, leaders.stream().map(CategoryLeaderResponse::from).toList()));
        return byCategory;
    }
}
