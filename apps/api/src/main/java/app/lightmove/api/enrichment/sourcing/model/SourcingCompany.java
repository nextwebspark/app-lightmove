package app.lightmove.api.enrichment.sourcing.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

/**
 * One company of a run, frozen when the run was requested: the row, its name for the strip, the
 * LinkedIn slug the people search keys on — null for a company whose row carries no page — its website
 * domain, and its headcount as the row states it; the last two null when unknown and on runs recorded
 * before they were kept.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SourcingCompany(UUID triageCompanyId, String companyName, String linkedinSlug, String domain,
                              Integer employeeCount) {

    public SearchedEmployer employer() {
        return new SearchedEmployer(linkedinSlug, domain, companyName);
    }
}
