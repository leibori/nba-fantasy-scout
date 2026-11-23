package com.ori_leibo.nbafantasyscout.api;

import com.ori_leibo.nbafantasyscout.api.dto.LeagueTeamResponse;
import com.ori_leibo.nbafantasyscout.roster.YahooLeagueService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/leagues")
public class LeagueController {

    private final YahooLeagueService yahoo;

    public LeagueController(YahooLeagueService yahoo) {
        this.yahoo = yahoo;
    }

    @Operation(summary = "The teams of a public Yahoo league (this season's), so the user can pick theirs")
    @GetMapping("/{leagueId}/teams")
    public List<LeagueTeamResponse> teams(@PathVariable long leagueId) {
        return yahoo.teams(leagueId).stream().map(LeagueTeamResponse::from).toList();
    }
}
