package com.ori_leibo.nbafantasyscout.api;

import com.ori_leibo.nbafantasyscout.api.dto.RosterRequest;
import com.ori_leibo.nbafantasyscout.api.dto.RosterResponse;
import com.ori_leibo.nbafantasyscout.api.dto.YahooRosterRequest;
import com.ori_leibo.nbafantasyscout.recommend.RecommendationService;
import com.ori_leibo.nbafantasyscout.roster.RosterService;
import com.ori_leibo.nbafantasyscout.roster.YahooLeagueService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/roster")
public class RosterController {

    private final RosterService roster;
    private final RecommendationService recommendations;
    private final YahooLeagueService yahoo;

    public RosterController(RosterService roster, RecommendationService recommendations, YahooLeagueService yahoo) {
        this.roster = roster;
        this.recommendations = recommendations;
        this.yahoo = yahoo;
    }

    @Operation(summary = "Keep / watch / drop for a roster typed as names, each drop with a free agent to pick up "
            + "instead, plus free agents worth picking up now; scored in the configured league format")
    @PostMapping
    public RosterResponse advise(@Valid @RequestBody RosterRequest request) {
        return RosterResponse.from(roster.advise(request.myRoster(), request.freeAgentsOrEmpty(),
                recommendations.defaultScoring()), List.of());
    }

    @Operation(summary = "The same advice for a team in a public Yahoo league: format, roster and free agents are "
            + "read from Yahoo")
    @PostMapping("/from-yahoo")
    public RosterResponse adviseFromYahoo(@Valid @RequestBody YahooRosterRequest request) {
        YahooLeagueService.LeagueTeam team = yahoo.read(request.leagueId(), request.teamName());
        return RosterResponse.from(roster.advise(team.roster(), team.freeAgents(), team.scoring()), team.warnings());
    }
}
