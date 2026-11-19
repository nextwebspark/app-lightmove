package app.lightmove.api.candidate.model;

import static app.lightmove.api.core.text.service.SuppliedText.blankToNull;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.modulith.NamedInterface;

/**
 * One post in an executive's career, as free text; {@code location} as the vendor stated it. {@code period} stays a
 * string because sources differ in precision. Unknown jsonb properties are ignored so a retired field never breaks a read.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@NamedInterface(name = "api", propagate = false)
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
