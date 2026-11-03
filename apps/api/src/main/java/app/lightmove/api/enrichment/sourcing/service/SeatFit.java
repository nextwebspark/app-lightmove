package app.lightmove.api.enrichment.sourcing.service;

import app.lightmove.api.enrichment.sourcing.constant.SuggestionReason;
import app.lightmove.api.enrichment.sourcing.constant.TitleLevel;
import app.lightmove.api.enrichment.sourcing.model.SearchWords;
import app.lightmove.api.enrichment.sourcing.model.SourcingBrief;
import java.util.List;
import java.util.Optional;

/**
 * Whether a current title fits the role: fixed rules, no model. The title names the function — a function term
 * or a standalone title, whole words — sits at the lowest level the search allows or above, and holds no
 * leave-out word. The general-management seat names no function, so for it the title must be that seat itself.
 */
record SeatFit(SearchWords words, TitleLevel lowest, boolean topSeat) {

    static SeatFit of(SourcingBrief brief, SearchWords words) {
        return new SeatFit(words, words.lowestLevel(brief.seniority()).titleLevel(),
                SearchWords.namesTheTopSeat(brief.roleTitle()));
    }

    /** Why a title does not hold the role, as a {@link SuggestionReason} code; empty when it does. */
    Optional<String> whyNot(String title) {
        Optional<String> leftOut = words.excludedWords().stream()
                .filter(word -> TitleWords.containsAny(title, List.of(word)))
                .findFirst();
        if (leftOut.isPresent()) {
            return Optional.of(SuggestionReason.LEAVE_OUT.with(leftOut.get()));
        }
        TitleLevel level = CurrentRole.levelOf(title);
        if (topSeat) {
            return level == TitleLevel.TOP && namesGeneralManagement(title) ? Optional.empty()
                    : Optional.of(SuggestionReason.NOT_THE_TOP_SEAT.code());
        }
        if (TitleWords.containsAny(title, words.standaloneTitles())) {
            return Optional.empty();
        }
        if (!level.isAtLeast(lowest)) {
            return Optional.of(SuggestionReason.BELOW_LEVEL.code());
        }
        return words.functionTerms().isEmpty() || TitleWords.containsAny(title, words.functionTerms())
                ? Optional.empty() : Optional.of(SuggestionReason.NO_FUNCTION_WORD.code());
    }

    /** The executive top seat, not a functional chief or a chair, though "CFO" and "Chairman" read as the top level. */
    private static boolean namesGeneralManagement(String title) {
        return TitleWords.containsAny(title, List.of("CEO", "Chief Executive", "Managing Director", "General Manager"))
                || (SearchWords.namesTheTopSeat(title) && !TitleWords.containsAny(title, List.of("Vice")));
    }
}
