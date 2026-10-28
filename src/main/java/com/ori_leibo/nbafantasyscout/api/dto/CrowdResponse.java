package com.ori_leibo.nbafantasyscout.api.dto;

import com.ori_leibo.nbafantasyscout.domain.TrendingSnapshot;
import com.ori_leibo.nbafantasyscout.recommend.CrowdSignal;

/**
 * Sleeper crowd evidence: {@code move} is ADD or DROP with its {@code count} of leagues in the last day,
 * both null when the player is in neither trending list.
 */
public record CrowdResponse(String signal, String move, Integer count) {

    public static CrowdResponse from(CrowdSignal signal, TrendingSnapshot snapshot) {
        return snapshot == null
                ? new CrowdResponse(signal.name(), null, null)
                : new CrowdResponse(signal.name(), snapshot.type().name(), snapshot.count());
    }
}
