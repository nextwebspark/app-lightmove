package app.lightmove.api.candidate.model;

import static app.lightmove.api.core.text.service.SuppliedText.blankToNull;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One post in an executive's career — free-text fields, not a pair of parsed dates. {@code location}
 * is where the post was held, as the vendor stated it; the nationality classifier weighs the country
 * of a first job, and nothing else fills it.
 *
 * <p>{@code period} stays a string ("2021–Present", "c. 2015") on purpose: a LinkedIn profile
 * publishes month precision, a conference bio a year, and a colleague's recollection neither, so
 * parsing would either refuse the entry or invent a precision the source never had.
 *
 * <p>Read back out of a jsonb column, so unknown properties are ignored for {@code StrategyFilter}'s
 * reason: a stored document must not become unreadable because a field was retired.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CandidateCareerEntry(String company, String title, String period, String location) {

    public CandidateCareerEntry {
        company = blankToNull(company);
        title = blankToNull(title);
        period = blankToNull(period);
        location = blankToNull(location);
    }

    /**
     * A row where the researcher filled nothing in — the empty trailing row every repeatable list grows.
     * {@code location} is deliberately not consulted: it describes a post, and a place with no company,
     * title or period is not one — kept, it would reach a prompt as "unknown role, unknown company".
     */
    public boolean isEmpty() {
        return company == null && title == null && period == null;
    }
}
