package app.lightmove.api.enrichment.sourcing.dto;

import app.lightmove.api.enrichment.sourcing.model.SearchWords;
import java.util.List;
import java.util.stream.Stream;

/**
 * The words a run searched by, so a researcher can see why it found whom it found: the level words, the
 * function terms with the standalone titles, and the words left out.
 */
public record SourcingSearchResponse(List<String> seniorityWords, List<String> functionWords,
                                     List<String> excludedWords) {

    static SourcingSearchResponse of(SearchWords words) {
        return words == null ? null : new SourcingSearchResponse(words.levelWords(),
                Stream.concat(words.functionTerms().stream(), words.standaloneTitles().stream()).toList(),
                words.excludedWords());
    }
}
