package app.lightmove.api.candidate.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * The list-shaped half of a profile, the {@code profile} jsonb column (read and written whole, V30's reasoning).
 * {@link #keepingEnrichmentOf} carries enrichment's fields across a drawer edit; {@code enrichedAt} is a string
 * because the jsonb mapper has no time module. Ignores unknown properties so a retired field (pre-V54 contacts) still reads.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CandidateProfile(List<CandidateCareerEntry> career, List<String> languages,
                               List<CandidateEducationEntry> education, List<String> skills,
                               String enrichedAt, ResearchedEmployerMark employer) {

    public CandidateProfile {
        career = career == null ? List.of() : career.stream().filter(entry -> !entry.isEmpty()).toList();
        languages = languages == null ? List.of() : List.copyOf(languages);
        education = education == null ? List.of()
                : education.stream().filter(entry -> !entry.isEmpty()).toList();
        skills = skills == null ? List.of() : List.copyOf(skills);
    }

    /** What a profile reads as before anyone has filled anything in. */
    public static CandidateProfile empty() {
        return new CandidateProfile(List.of(), List.of(), List.of(), List.of(), null, null);
    }

    /** The drawer edits what it renders; the components it has never heard of ride along untouched. */
    public CandidateProfile keepingEnrichmentOf(CandidateProfile existing) {
        return new CandidateProfile(career, languages, existing.education(), existing.skills(),
                existing.enrichedAt(), existing.employer());
    }
}
