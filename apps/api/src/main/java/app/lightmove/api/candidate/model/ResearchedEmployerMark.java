package app.lightmove.api.candidate.model;

import static app.lightmove.api.core.text.service.SuppliedText.blankToNull;

import app.lightmove.api.core.text.service.LinkedInUrls;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The employer the last research named, kept by identity — the company page's slug — beside the logo
 * the vendor sent with it. The logo is a hint, not a record: a LinkedIn CDN URL is signed and expires,
 * so the Candidates page prefers the mandate's own company row and falls back to this one.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ResearchedEmployerMark(String name, String linkedinSlug, String logoUrl) {

    public ResearchedEmployerMark {
        name = blankToNull(name);
        linkedinSlug = blankToNull(linkedinSlug);
        logoUrl = blankToNull(logoUrl);
    }

    /** Null when research named no employer at all. */
    public static ResearchedEmployerMark of(EnrichedProfile enriched) {
        ResearchedEmployerMark mark = new ResearchedEmployerMark(enriched.employerName(),
                LinkedInUrls.companySlugOrNull(enriched.employerLinkedinUrl()), enriched.employerLogoUrl());
        return mark.name() == null && mark.linkedinSlug() == null ? null : mark;
    }

    public boolean names(String employer) {
        return name != null && employer != null && name.strip().equalsIgnoreCase(employer.strip());
    }
}
