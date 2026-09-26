package app.lightmove.api.enrichment.company.model;

import app.lightmove.api.triagecompany.model.CapturedCompanyDetails;
import java.util.List;
import java.util.Optional;

/**
 * What a provider said about one LinkedIn company page, V2 industry leaf and payload included — what
 * the cache keeps. {@link #asCapturedDetails()} is the one place the mandate-facing record is built,
 * so a cache hit and a fresh call produce the same row.
 */
public record VendorCompanyRecord(String linkedinSlug, String companyName, String industry,
                                  String companyCountry, String companyCity,
                                  Integer employeesInLinkedin, String website, String linkedinUrl,
                                  Integer foundedYear, String about, String logoUrl,
                                  List<String> keywords, String raw) {

    public Optional<CapturedCompanyDetails> asCapturedDetails() {
        if (companyName == null || companyName.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new CapturedCompanyDetails(companyName, industry, companyCountry,
                companyCity, employeesInLinkedin, null, website, linkedinUrl, foundedYear, about,
                logoUrl, null, null));
    }
}
