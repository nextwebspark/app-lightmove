package app.lightmove.api.enrichment.sourcing.dto;

import app.lightmove.api.enrichment.sourcing.model.SourcingSpec;
import java.util.List;

/** The title words a run searched by, so a researcher can see why it found whom it found. */
public record SourcingSearchResponse(List<String> seniorityWords, List<String> functionWords,
                                     List<String> excludedWords) {

    static SourcingSearchResponse of(SourcingSpec spec) {
        return spec == null ? null
                : new SourcingSearchResponse(spec.seniorityWords(), spec.functionWords(), spec.excludedWords());
    }
}
