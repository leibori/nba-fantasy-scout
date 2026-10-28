package com.ori_leibo.nbafantasyscout.api;

import com.ori_leibo.nbafantasyscout.api.dto.CategoryChangeResponse;
import com.ori_leibo.nbafantasyscout.api.dto.PlayerFormResponse;
import com.ori_leibo.nbafantasyscout.domain.StatCategory;
import com.ori_leibo.nbafantasyscout.recommend.RecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recommendations")
public class RecommendationController {

    private final RecommendationService recommendations;

    public RecommendationController(RecommendationService recommendations) {
        this.recommendations = recommendations;
    }

    @Operation(summary = "Rising players (hot, early hot, returning), best value first")
    @GetMapping("/hot")
    public List<PlayerFormResponse> hot(@RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {
        return recommendations.hot(limit).stream().map(PlayerFormResponse::from).toList();
    }

    @Operation(summary = "Biggest improvement in one Yahoo category: FG_PCT, FT_PCT, THREES, PTS, REB, AST, STL, BLK, TO")
    @GetMapping("/{category}")
    public List<CategoryChangeResponse> byCategory(@PathVariable String category,
                                                   @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {
        StatCategory parsed = parse(category);
        return recommendations.byCategory(parsed, limit).stream()
                .map(change -> CategoryChangeResponse.from(parsed.name(), change))
                .toList();
    }

    // case-insensitive, so /recommendations/blk works too
    private static StatCategory parse(String category) {
        try {
            return StatCategory.valueOf(category.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown category \"%s\"; use one of %s"
                    .formatted(category, Arrays.toString(StatCategory.values())));
        }
    }
}
