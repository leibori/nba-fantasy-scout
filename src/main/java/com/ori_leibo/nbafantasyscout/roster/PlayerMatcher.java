package com.ori_leibo.nbafantasyscout.roster;

import com.ori_leibo.nbafantasyscout.domain.Player;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Finds the player a name refers to when there is no shared id (Sleeper names, screenshot text).
 * Names are normalised, then compared with Levenshtein distance; a weak or tied match is flagged unsure.
 */
public class PlayerMatcher {

    private static final Set<String> SUFFIXES = Set.of("jr", "sr", "ii", "iii", "iv");

    private final double minConfidence;

    public PlayerMatcher(double minConfidence) {
        if (minConfidence < 0 || minConfidence > 1) {
            throw new IllegalArgumentException("minConfidence must be between 0 and 1, was " + minConfidence);
        }
        this.minConfidence = minConfidence;
    }

    /** {@code confidence} is 1.0 for an exact match after normalisation, lower the more edits it took. */
    public record Match(Player player, double confidence, boolean unsure) {
    }

    /** Empty when the name is blank or there are no players to match against. */
    public Optional<Match> match(String name, Collection<Player> players) {
        String query = normalize(name);
        if (query.isEmpty()) {
            return Optional.empty();
        }
        // "J. Smith" is compared with every player's first name cut to its initial
        boolean initialOnly = query.indexOf(' ') == 1;

        Player best = null;
        double bestScore = -1;
        boolean tied = false;
        for (Player player : players) {
            String candidate = normalize(player.name());
            double score = similarity(query, initialOnly ? toInitial(candidate) : candidate);
            if (score > bestScore) {
                best = player;
                bestScore = score;
                tied = false;
            } else if (score == bestScore) {
                tied = true;
            }
        }
        if (best == null) {
            return Optional.empty();
        }
        return Optional.of(new Match(best, bestScore, tied || bestScore < minConfidence));
    }

    // "Nikola Jokić" -> "nikola jokic", "Jaren Jackson Jr." -> "jaren jackson", "Gilgeous-Alexander" -> "gilgeous alexander"
    private static String normalize(String name) {
        String plain = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("['.]", "")
                .replaceAll("[^a-z]+", " ");
        return Arrays.stream(plain.trim().split(" "))
                .filter(token -> !token.isEmpty() && !SUFFIXES.contains(token))
                .collect(Collectors.joining(" "));
    }

    private static String toInitial(String name) {
        int space = name.indexOf(' ');
        return space < 0 ? name : name.charAt(0) + name.substring(space);
    }

    private static double similarity(String a, String b) {
        int longest = Math.max(a.length(), b.length());
        return longest == 0 ? 1 : 1 - (double) levenshtein(a, b) / longest;
    }

    // classic edit distance, keeping only the previous row of the DP table
    static int levenshtein(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int substitution = previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                current[j] = Math.min(substitution, Math.min(previous[j] + 1, current[j - 1] + 1));
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }
}
