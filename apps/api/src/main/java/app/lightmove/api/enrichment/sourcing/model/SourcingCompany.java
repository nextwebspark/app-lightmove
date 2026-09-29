package app.lightmove.api.enrichment.sourcing.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

/**
 * One company of a run, frozen when the run was requested: the row, its name for the strip, and the
 * LinkedIn slug the people search keys on — null for a company whose row carries no page.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SourcingCompany(UUID triageCompanyId, String companyName, String linkedinSlug) {}
