package com.ori_leibo.nbafantasyscout.api.dto;

import com.ori_leibo.nbafantasyscout.client.YahooMapper;

/** A team in a Yahoo league; {@code logoUrl} is null when Yahoo has none we may show. */
public record LeagueTeamResponse(String name, String logoUrl) {

    public static LeagueTeamResponse from(YahooMapper.Team team) {
        return new LeagueTeamResponse(team.name(), team.logoUrl());
    }
}
