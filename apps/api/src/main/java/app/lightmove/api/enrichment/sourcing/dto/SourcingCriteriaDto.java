package app.lightmove.api.enrichment.sourcing.dto;

import app.lightmove.api.enrichment.sourcing.constant.SearchLevel;
import app.lightmove.api.enrichment.sourcing.model.SearchCriteria;
import app.lightmove.api.enrichment.sourcing.model.SearchWords;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * A position's search as the panel edits it: the levels, the title words (function terms and the titles that stand
 * alone), the market's own level words, the words left out, the suggestions, where people may live and the live
 * profiles a company may buy. The words are cleaned as {@link SearchWords} cleans them whoever sends them.
 */
public record SourcingCriteriaDto(
        @NotNull @Size(max = 5) List<SearchLevel> levels,
        @NotNull @Size(max = SearchWords.MAX_FUNCTION_TERMS, message = "At most 12 title words")
        List<@Size(max = 60, message = "A title word must be 60 characters or fewer") String> functionTerms,
        @NotNull @Size(max = SearchWords.MAX_STANDALONE_TITLES)
        List<@Size(max = 60, message = "A title must be 60 characters or fewer") String> standaloneTitles,
        @Size(max = SearchWords.MAX_LOCAL_LEVEL_WORDS) List<@Size(max = 60) String> localLevelWords,
        @NotNull @Size(max = SearchWords.MAX_EXCLUDED_WORDS, message = "At most 4 words left out")
        List<@Size(max = 60, message = "A word must be 60 characters or fewer") String> excludedWords,
        @Size(max = SearchWords.MAX_SUGGESTIONS) List<@Size(max = 60) String> suggestions,
        @Size(max = 300) String roleSummary,
        @NotNull @Size(max = 12) List<@Pattern(regexp = "[A-Za-z]{2}", message = "A country is a two-letter code") String> countryCodes,
        int perCompany
) {

    public static SourcingCriteriaDto of(SearchCriteria criteria) {
        SearchWords words = criteria.words();
        return new SourcingCriteriaDto(words.levels(), words.functionTerms(), words.standaloneTitles(),
                words.localLevelWords(), words.excludedWords(), words.suggestions(), words.roleSummary(),
                criteria.countryCodes(), criteria.perCompany());
    }

    public SearchCriteria toCriteria() {
        return new SearchCriteria(new SearchWords(levels, functionTerms, standaloneTitles, localLevelWords,
                excludedWords, suggestions, roleSummary), countryCodes, perCompany);
    }
}
