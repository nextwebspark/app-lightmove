package app.lightmove.api.enrichment.company.service;

import app.lightmove.api.enrichment.company.model.VendorCompanyRecord;
import java.util.List;
import java.util.Optional;

/**
 * Researches a company's LinkedIn slug. A port so the flow tests end to end with no vendor; empty
 * means not researched and the row keeps what the plugin read.
 */
public interface LinkedInCompanyEnricher {

    Optional<VendorCompanyRecord> fetch(String linkedinSlug);

    /** In {@code countryCode} (anywhere when null). Every hit is billed, so a provider answers a handful. */
    default List<VendorCompanyRecord> searchByName(String namePart, String countryCode, int minEmployees) {
        return List.of();
    }

    /** Stored beside every cached record, a miss included. */
    String provider();

    /**
     * False for the no-vendor stand-in, whose silence is not an answer — {@code CompanyResearch} would
     * otherwise cache a miss for every slug captured before a key is set.
     */
    default boolean isEnabled() {
        return true;
    }
}
