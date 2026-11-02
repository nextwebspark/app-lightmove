package app.lightmove.api.enrichment.sourcing.service;

import app.lightmove.api.enrichment.candidate.model.BrightDataPerson;
import app.lightmove.api.enrichment.sourcing.constant.TitleLevel;
import app.lightmove.api.enrichment.sourcing.model.SourcingBrief;
import app.lightmove.api.enrichment.sourcing.model.SourcingSpec;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Whether a person a search found holds the brief's seat closely enough to be filed, read from the job
 * they hold now by fixed rules no model takes part in: the title names the function as a whole word, sits
 * at or above the lowest level the seat allows, and carries no excluded word. The general-management
 * seat names no function, so there the title must be that seat itself. Every search, provider and
 * reworded round answers through this one check, so none of them can file a pharmacist as a Head of IT.
 */
record SeatFit(List<String> briefFunctionWords, List<String> excludedWords, TitleLevel lowest, boolean topSeat) {

    /**
     * Words the titles of many functions carry — "Business Development", "Surgical Infrastructure",
     * "Medical Systems" — so one a reworded round searched never proves the function by itself; the
     * brief's own words still do.
     */
    private static final Set<String> SHARED_WORDS = Set.of("development", "system", "solution", "infrastructure",
            "network", "service", "project", "program", "programme", "operation", "operational", "business",
            "strategy", "strategic", "transformation", "planning", "management", "support", "application",
            "engineering", "technical", "innovation", "excellence", "performance", "quality", "commercial",
            "corporate", "administration", "process", "product", "enterprise", "security", "growth");

    /** The seat as the run's first words read it; a list naming no function falls back to the title's own words. */
    static SeatFit of(SourcingBrief brief, SourcingSpec spec) {
        boolean topSeat = SourcingSpec.namesTheTopSeat(brief.roleTitle());
        List<String> functionWords = spec.functionNamingWords();
        if (functionWords.isEmpty() && !topSeat) {
            functionWords = SourcingSpec.defaultFor(brief.roleTitle(), brief.seniority()).functionNamingWords();
        }
        return new SeatFit(functionWords, spec.excludedWords(), TitleLevel.lowestFor(brief.seniority()),
                topSeat || functionWords.isEmpty());
    }

    boolean holds(BrightDataPerson person, SourcingSpec round) {
        return holds(SourcedHitRanking.currentRole(person), round);
    }

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
        return level.isAtLeast(lowest) && TitleWords.containsFunction(title, functionWordsWith(round));
    }

    private List<String> functionWordsWith(SourcingSpec round) {
        return Stream.concat(briefFunctionWords.stream(),
                        round.functionNamingWords().stream().filter(word -> !isShared(word)))
                .distinct()
                .toList();
    }

    private static boolean isShared(String word) {
        String folded = word.toLowerCase(Locale.ROOT);
        return SHARED_WORDS.contains(folded) || folded.endsWith("s")
                && SHARED_WORDS.contains(folded.substring(0, folded.length() - 1));
    }

    /** The top seat itself, not a functional chief: "Chief Financial Officer" reads as the top level too. */
    private static boolean namesGeneralManagement(String title) {
        return TitleWords.containsAny(title, List.of("CEO", "Chairman", "Chairwoman", "Chairperson",
                "Chief Executive", "Managing Director", "General Manager"))
                || TitleWords.containsAny(title, List.of("President")) && !TitleWords.containsAny(title, List.of("Vice"));
    }
}
