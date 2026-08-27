package app.lightmove.api.candidate.model;

import static app.lightmove.api.core.text.service.SuppliedText.blankToNull;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One post in an executive's career — the shape a researcher actually writes it in, which is three
 * free-text fields and not a pair of parsed dates.
 *
 * <p>{@code period} stays a string ("2021–Present", "2017–2021", "c. 2015") on purpose. A LinkedIn
 * profile publishes month precision, a conference bio publishes a year, and a colleague's recollection
 * publishes neither; parsing all of that into a date range would either refuse the entry or invent a
 * precision the source never had.
 *
 * <p>Read back out of a jsonb column, so unknown properties are ignored for the reason
 * {@code StrategyFilter} spells out: a stored document must not become unreadable because a field was
 * later retired.
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
