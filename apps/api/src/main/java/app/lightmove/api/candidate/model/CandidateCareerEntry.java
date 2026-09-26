package app.lightmove.api.candidate.model;

import static app.lightmove.api.core.text.service.SuppliedText.blankToNull;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One post in an executive's career — three free-text fields, not a pair of parsed dates.
 *
 * <p>{@code period} stays a string ("2021–Present", "c. 2015") on purpose: a LinkedIn profile
 * publishes month precision, a conference bio a year, and a colleague's recollection neither, so
 * parsing would either refuse the entry or invent a precision the source never had.
 *
 * <p>Read back out of a jsonb column, so unknown properties are ignored for {@code StrategyFilter}'s
 * reason: a stored document must not become unreadable because a field was retired.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CandidateCareerEntry(String company, String title, String period) {

    public CandidateCareerEntry {
        company = blankToNull(company);
        title = blankToNull(title);
        period = blankToNull(period);
    }

    /** A row where the researcher filled nothing in — the empty trailing row every repeatable list grows. */
    public boolean isEmpty() {
        return company == null && title == null && period == null;
    }
}
