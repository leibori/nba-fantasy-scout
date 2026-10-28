package com.ori_leibo.nbafantasyscout.api.dto;

import com.ori_leibo.nbafantasyscout.domain.Player;
import com.ori_leibo.nbafantasyscout.recommend.TrendService.PlayerForm;

/**
 * A player's form as the API shows it. {@code hotScore}, {@code baselineSource}, {@code recent} and
 * {@code baseline} are null when there is no trend yet; {@code value} is null with no games this season.
 */
public record PlayerFormResponse(long id, String name, String team, String position, String injuryStatus,
                                 String form, int gamesThisSeason, Double value, Double hotScore,
                                 String baselineSource, boolean provisional,
                                 StatLineResponse recent, StatLineResponse baseline, CrowdResponse crowd) {

    public static PlayerFormResponse from(PlayerForm form) {
        Player player = form.player();
        boolean hasTrend = form.trend() != null;
        return new PlayerFormResponse(player.id(), player.name(), player.team(), player.position(), player.status(),
                form.form().name(), form.gamesThisSeason(),
                form.value() == null ? null : StatLineResponse.round(form.value()),
                form.hotScore() == null ? null : StatLineResponse.round(form.hotScore()),
                hasTrend ? form.trend().baselineSource().name() : null,
                hasTrend && form.trend().provisional(),
                hasTrend ? StatLineResponse.from(form.trend().recent()) : null,
                hasTrend ? StatLineResponse.from(form.trend().baseline()) : null,
                CrowdResponse.from(form.crowdSignal(), form.crowd()));
    }
}
