package com.ori_leibo.nbafantasyscout.api;

import com.ori_leibo.nbafantasyscout.api.dto.PlayerFormResponse;
import com.ori_leibo.nbafantasyscout.recommend.RecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.NoSuchElementException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/players")
public class PlayerController {

    private final RecommendationService recommendations;

    public PlayerController(RecommendationService recommendations) {
        this.recommendations = recommendations;
    }

    @Operation(summary = "One player's trend, value, form and crowd signal, by ESPN player id")
    @GetMapping("/{id}/trend")
    public PlayerFormResponse trend(@PathVariable long id) {
        return recommendations.player(id)
                .map(PlayerFormResponse::from)
                .orElseThrow(() -> new NoSuchElementException("No rostered player with id " + id));
    }
}
