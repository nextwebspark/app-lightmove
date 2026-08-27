package app.lightmove.api.candidate.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * The list-shaped half of a profile, stored as the {@code profile} jsonb column.
 *
 * <p>A career history is one fact about one person rather than a relation: it is read whole, written
 * whole, and no query will ever ask "which candidates held a title in 2019" — the same argument V30
 * makes for the strategy filter, and the reason neither of these becomes a child table. Languages sit
 * beside it for the same reason.
 *
 * <p>Null-tolerant on the way in, and {@code @JsonIgnoreProperties} for the same reason
 * {@code StrategyFilter} carries it: this record reads documents written by earlier versions of
 * itself, and a profile must never become unreadable because a field was retired.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CandidateProfile(List<CandidateCareerEntry> career, List<String> languages) {

    public CandidateProfile {
        career = career == null ? List.of() : career.stream().filter(entry -> !entry.isEmpty()).toList();
        languages = languages == null ? List.of() : List.copyOf(languages);
    }

    /** What a profile reads as before anyone has filled either list in. */
    public static CandidateProfile empty() {
        return new CandidateProfile(List.of(), List.of());
    }
}
