package app.lightmove.api.candidate.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * The list-shaped half of a profile, stored as the {@code profile} jsonb column. A career history is
 * read whole and written whole and no query will ask "which candidates held a title in 2019" — V30's
 * argument for the strategy filter, and why neither becomes a child table.
 *
 * <p>{@code education}, {@code skills} and {@code enrichedAt} are enrichment's, carried across a
 * drawer edit by {@link #keepingEnrichmentOf} because the drawer resubmits only what it renders.
 * {@code enrichedAt} is an ISO-8601 string rather than an {@code Instant} — the jsonb mapper is a bare
 * Jackson 2 {@code ObjectMapper} with no time module, and a type it cannot read back would make every
 * enriched profile unreadable.
 *
 * <p>Null-tolerant on the way in, and {@code @JsonIgnoreProperties} for {@code StrategyFilter}'s
 * reason: a profile must never become unreadable because a field was retired.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CandidateProfile(List<CandidateCareerEntry> career, List<String> languages,
                               List<CandidateEducationEntry> education, List<String> skills,
                               String enrichedAt) {

    public CandidateProfile {
        career = career == null ? List.of() : career.stream().filter(entry -> !entry.isEmpty()).toList();
        languages = languages == null ? List.of() : List.copyOf(languages);
        education = education == null ? List.of()
                : education.stream().filter(entry -> !entry.isEmpty()).toList();
        skills = skills == null ? List.of() : List.copyOf(skills);
    }

    /** What a profile reads as before anyone has filled anything in. */
    public static CandidateProfile empty() {
        return new CandidateProfile(List.of(), List.of(), List.of(), List.of(), null);
    }

    /** The drawer edits what it renders; the components it has never heard of ride along untouched. */
    public CandidateProfile keepingEnrichmentOf(CandidateProfile existing) {
        return new CandidateProfile(career, languages, existing.education(), existing.skills(),
                existing.enrichedAt());
    }
}
