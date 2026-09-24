package app.lightmove.api.enrichment.company.service;

import app.lightmove.api.enrichment.company.model.VendorCompanyRecord;
import java.util.List;
import java.util.Optional;

/**
 * Researches a company's LinkedIn slug into the facts a consultant would otherwise type by hand —
 * industry, size, HQ, website, founded, description. A port for the reason the person enricher is
 * one: the flow tests end to end with no vendor, and swapping the provider is one adapter. Empty
 * means the company could not be researched, and the row simply keeps what the plugin read.
 */
public interface LinkedInCompanyEnricher {

    Optional<VendorCompanyRecord> fetch(String linkedinSlug);

    /**
     * The pages whose name contains {@code namePart}, in one country — or anywhere, for a null
     * {@code countryCode} — with at least {@code minEmployees}. Every hit is billed, so a provider
     * answers a handful rather than every match.
     */
    default List<VendorCompanyRecord> searchByName(String namePart, String countryCode, int minEmployees) {
        return List.of();
    }

    /** Who answered. Stored beside every cached record, a miss included, so a row names its source. */
    String provider();

    /**
     * False for the stand-in that runs with no vendor configured. Its silence is not an answer:
     * {@code CompanyResearch} would otherwise remember a miss for every slug captured before a key is
     * set, and keep answering with it for the cache's whole TTL afterwards.
     */
    default boolean isEnabled() {
        return true;
    }
}
