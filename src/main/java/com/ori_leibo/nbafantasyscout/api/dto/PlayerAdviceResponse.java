package com.ori_leibo.nbafantasyscout.api.dto;

import com.ori_leibo.nbafantasyscout.roster.Recommendation;
import com.ori_leibo.nbafantasyscout.roster.RosterService.PlayerAdvice;

/**
 * The advice for one roster name. {@code unsure}: the name matched weakly, check {@code player} is the one meant;
 * {@code replacement} is set only for {@code DROP}.
 */
public record PlayerAdviceResponse(String name, boolean unsure, String advice, String reason,
                                   PlayerFormResponse player, PlayerFormResponse replacement) {

    public static PlayerAdviceResponse from(PlayerAdvice advice) {
        PlayerFormResponse player = PlayerFormResponse.from(advice.form());
        return switch (advice.recommendation()) {
            case Recommendation.Keep keep -> new PlayerAdviceResponse(advice.name(), advice.unsure(), "KEEP",
                    keep.reason(), player, null);
            case Recommendation.Watch watch -> new PlayerAdviceResponse(advice.name(), advice.unsure(), "WATCH",
                    watch.reason(), player, null);
            case Recommendation.Drop drop -> new PlayerAdviceResponse(advice.name(), advice.unsure(), "DROP",
                    drop.reason(), player, PlayerFormResponse.from(drop.replacement()));
        };
    }
}
