package app.lightmove.api.candidate.model;

import static app.lightmove.api.core.text.service.SuppliedText.blankToNull;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** The employer the last research named, with the logo the vendor sent — a hint, since a LinkedIn CDN URL expires. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ResearchedEmployerMark(String name, String logoUrl) {

    public ResearchedEmployerMark {
        name = blankToNull(name);
        logoUrl = blankToNull(logoUrl);
    }

    /** Null unless research named the employer: a nameless mark never matches, yet would replace one that can. */
    public static ResearchedEmployerMark of(EnrichedProfile enriched) {
        return enriched.employerName() == null ? null
                : new ResearchedEmployerMark(enriched.employerName(), enriched.employerLogoUrl());
    }

    public boolean names(String employer) {
        return name != null && employer != null && name.strip().equalsIgnoreCase(employer.strip());
    }
}
