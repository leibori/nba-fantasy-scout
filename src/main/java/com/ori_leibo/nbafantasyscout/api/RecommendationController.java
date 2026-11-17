package com.ori_leibo.nbafantasyscout.api;

import com.ori_leibo.nbafantasyscout.api.dto.CategoryChangeResponse;
import com.ori_leibo.nbafantasyscout.api.dto.PlayerFormResponse;
import com.ori_leibo.nbafantasyscout.domain.LeagueScoring;
import com.ori_leibo.nbafantasyscout.domain.StatCategory;
import com.ori_leibo.nbafantasyscout.recommend.RecommendationService;
import com.ori_leibo.nbafantasyscout.recommend.TrendService.PlayerForm;
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

    @Operation(summary = "Rising players (hot, early hot, returning), best value first; "
            + "scoring = points or categories, the configured format when left out")
    @GetMapping("/hot")
    public List<PlayerFormResponse> hot(@RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit,
                                        @RequestParam(required = false) String scoring) {
        List<PlayerForm> forms = scoring == null
                ? recommendations.hot(limit)
                : recommendations.hot(limit, parse(LeagueScoring.Format.class, scoring, "scoring"));
        return forms.stream().map(PlayerFormResponse::from).toList();
    }

    @Operation(summary = "Biggest improvement in one Yahoo category: FG_PCT, FT_PCT, THREES, PTS, REB, AST, STL, BLK, TO")
    @GetMapping("/{category}")
    public List<CategoryChangeResponse> byCategory(@PathVariable String category,
                                                   @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {
        StatCategory parsed = parse(StatCategory.class, category, "category");
        return recommendations.byCategory(parsed, limit).stream()
                .map(change -> CategoryChangeResponse.from(parsed.name(), change))
                .toList();
    }

    // case-insensitive, so /recommendations/blk and ?scoring=Points work too
    private static <E extends Enum<E>> E parse(Class<E> type, String value, String what) {
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown %s \"%s\"; use one of %s"
                    .formatted(what, value, Arrays.toString(type.getEnumConstants())));
        }
    }
}
