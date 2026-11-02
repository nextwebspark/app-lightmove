package app.lightmove.api.enrichment.sourcing.service;

import app.lightmove.api.enrichment.candidate.model.BrightDataPerson;
import app.lightmove.api.enrichment.sourcing.constant.TitleLevel;
import app.lightmove.api.enrichment.sourcing.model.SourcingBrief;
import app.lightmove.api.enrichment.sourcing.model.SourcingSpec;
import java.util.List;
import java.util.stream.Stream;

/**
 * Whether a person a search found holds the brief's seat closely enough to be filed: fixed rules over the
 * job held now, no model, the one check every provider and reworded round answers through.
 */
record SeatFit(List<String> briefFunctionWords, List<String> excludedWords, TitleLevel lowest, boolean topSeat) {

    /**
     * The run's first function words, else the role title's own. A brief naming no function at all is judged
     * by level and exclusions alone, never read as the general-management seat.
     */
    static SeatFit of(SourcingBrief brief, SourcingSpec spec) {
        List<String> functionWords = spec.functionNamingWords();
        if (functionWords.isEmpty()) {
            functionWords = SourcingSpec.functionWordsOfTitle(brief.roleTitle());
        }
        return new SeatFit(functionWords, spec.excludedWords(), TitleLevel.lowestFor(brief.seniority()),
                SourcingSpec.namesTheTopSeat(brief.roleTitle()));
    }

    boolean holds(BrightDataPerson person, SourcingSpec round) {
        return holds(SourcedHitRanking.currentRole(person), round);
    }

    /** A reworded round's own words count only where no other function shares them. */
    boolean holds(String title, SourcingSpec round) {
        if (title == null || title.isBlank()
                || TitleWords.containsAny(title, excludedWords)
                || TitleWords.containsAny(title, round.excludedWords())) {
            return false;
        }
        TitleLevel level = SourcedHitRanking.levelOf(title);
        if (topSeat) {
            return level == TitleLevel.TOP && namesGeneralManagement(title);
        }
        if (!level.isAtLeast(lowest)) {
            return false;
        }
        return briefFunctionWords.isEmpty() || TitleWords.containsFunction(title,
                Stream.concat(briefFunctionWords.stream(), round.distinctiveFunctionWords().stream()).distinct()
                        .toList());
    }

    /** The executive top seat, not a functional chief or a chair, though "CFO" and "Chairman" read as the top level. */
    private static boolean namesGeneralManagement(String title) {
        return TitleWords.containsAny(title, List.of("CEO", "Chief Executive", "Managing Director", "General Manager"))
                || (SourcingSpec.namesTheTopSeat(title) && !TitleWords.containsAny(title, List.of("Vice")));
    }
}
