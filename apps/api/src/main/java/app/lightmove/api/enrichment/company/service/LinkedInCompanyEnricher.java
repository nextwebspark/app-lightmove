package app.lightmove.api.enrichment.company.service;

import app.lightmove.api.triagecompany.model.CapturedCompanyDetails;
import java.util.Optional;

/**
 * Researches a company's LinkedIn slug into the facts a consultant would otherwise type by hand —
 * industry, size, HQ, website, founded, description. A port for the reason the person enricher is
 * one: the flow tests end to end with no vendor, and swapping the provider is one adapter. Empty
 * means the company could not be researched, and the row simply keeps what the plugin read.
 */
public interface LinkedInCompanyEnricher {

    Optional<CapturedCompanyDetails> fetch(String linkedinSlug);
}
