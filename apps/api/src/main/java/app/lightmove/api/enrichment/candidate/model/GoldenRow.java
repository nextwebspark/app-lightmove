package app.lightmove.api.enrichment.candidate.model;

import app.lightmove.api.candidate.constant.BackgroundField;
import app.lightmove.api.candidate.model.CandidateCareerEntry;
import app.lightmove.api.candidate.model.CandidateDossier;
import app.lightmove.api.candidate.model.CandidateEducationEntry;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Set;

/**
 * One labelled profile of the nationality golden set — the shape {@code eval/golden-row.schema.json}
 * documents. The profile carries only what {@link CandidateDossier} allows, so a row can never put a
 * contact, a salary or a note in front of the model.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GoldenRow(String id, String labelSource, GoldenProfile profile, GoldenLabel expected, String note) {

    public CandidateDossier dossierMissing(Set<BackgroundField> missing) {
        return profile.dossierMissing(missing);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GoldenProfile(String fullName, String title, String companyName, String locationCity,
                                String locationCountry, String summary, List<CandidateCareerEntry> career,
                                List<CandidateEducationEntry> education, List<String> skills,
                                List<String> languages) {

        public CandidateDossier dossierMissing(Set<BackgroundField> missing) {
            return new CandidateDossier(fullName, title, companyName, locationCity, locationCountry, null, summary,
                    orEmpty(career), orEmpty(education), orEmpty(skills), orEmpty(languages), missing);
        }

        private static <T> List<T> orEmpty(List<T> values) {
            return values == null ? List.of() : values;
        }
    }

    /** {@code nationality} is a group label or "Unknown"; {@code seniority} is "N-1" etc., or null when unlabelled. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GoldenLabel(String nationality, String seniority) {}
}
