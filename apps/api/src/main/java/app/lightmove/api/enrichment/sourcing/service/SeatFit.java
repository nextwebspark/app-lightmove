package app.lightmove.api.enrichment.sourcing.service;

import app.lightmove.api.enrichment.candidate.model.BrightDataPerson;
import app.lightmove.api.enrichment.sourcing.constant.TitleLevel;
import app.lightmove.api.enrichment.sourcing.model.SearchWords;
import app.lightmove.api.enrichment.sourcing.model.SourcingBrief;
import java.util.List;

/**
 * Whether a person found holds the role closely enough to be filed: fixed rules over the job held now, no
 * model. The title names the function — a function term or a standalone title, whole words — sits at the
 * lowest level the search allows or above, and holds no excluded word. The general-management seat names
 * no function, so for it the title must be that seat itself.
 */
record SeatFit(SearchWords words, TitleLevel lowest, boolean topSeat) {

    static SeatFit of(SourcingBrief brief, SearchWords words) {
        return new SeatFit(words, words.lowestLevel(brief.seniority()).titleLevel(),
                SearchWords.namesTheTopSeat(brief.roleTitle()));
    }

    boolean holds(BrightDataPerson person) {
        return holds(SourcedHitRanking.currentRole(person));
    }

    boolean holds(String title) {
        if (title == null || title.isBlank() || TitleWords.containsAny(title, words.excludedWords())) {
            return false;
        }
        TitleLevel level = SourcedHitRanking.levelOf(title);
        if (topSeat) {
            return level == TitleLevel.TOP && namesGeneralManagement(title);
        }
        if (TitleWords.containsAny(title, words.standaloneTitles())) {
            return true;
        }
        if (!level.isAtLeast(lowest)) {
            return false;
        }
        return words.functionTerms().isEmpty() || TitleWords.containsAny(title, words.functionTerms());
    }

    /** The executive top seat, not a functional chief or a chair, though "CFO" and "Chairman" read as the top level. */
    private static boolean namesGeneralManagement(String title) {
        return TitleWords.containsAny(title, List.of("CEO", "Chief Executive", "Managing Director", "General Manager"))
                || (SearchWords.namesTheTopSeat(title) && !TitleWords.containsAny(title, List.of("Vice")));
    }
}
