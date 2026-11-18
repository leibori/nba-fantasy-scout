package com.ori_leibo.nbafantasyscout.api;

import com.ori_leibo.nbafantasyscout.api.dto.RosterRequest;
import com.ori_leibo.nbafantasyscout.api.dto.RosterResponse;
import com.ori_leibo.nbafantasyscout.recommend.RecommendationService;
import com.ori_leibo.nbafantasyscout.roster.RosterService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/roster")
public class RosterController {

    private final RosterService roster;
    private final RecommendationService recommendations;

    public RosterController(RosterService roster, RecommendationService recommendations) {
        this.roster = roster;
        this.recommendations = recommendations;
    }

    @Operation(summary = "Keep / watch / drop for a roster typed as names, each drop with a free agent to pick up "
            + "instead, plus free agents worth picking up now; scored in the configured league format")
    @PostMapping
    public RosterResponse advise(@Valid @RequestBody RosterRequest request) {
        return RosterResponse.from(roster.advise(request.myRoster(), request.freeAgentsOrEmpty(),
                recommendations.defaultScoring()));
    }
}
