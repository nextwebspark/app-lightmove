package app.lightmove.api.candidate.model;

import static app.lightmove.api.core.text.service.SuppliedText.blankToNull;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One school in an executive's education, shaped like {@link CandidateCareerEntry} and for the same
 * reasons: {@code period} stays free text because sources publish anything from month precision to
 * nothing, and unknown properties are ignored so a stored document survives a retired field.
 *
 * <p>Written only by enrichment today — no screen edits it yet.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CandidateEducationEntry(String school, String degree, String period) {

    public CandidateEducationEntry {
        school = blankToNull(school);
        degree = blankToNull(degree);
        period = blankToNull(period);
    }

    public boolean isEmpty() {
        return school == null && degree == null && period == null;
    }
}
