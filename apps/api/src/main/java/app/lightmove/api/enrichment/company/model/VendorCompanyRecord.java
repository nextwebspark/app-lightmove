package app.lightmove.api.enrichment.company.model;

import app.lightmove.api.triagecompany.model.CapturedCompanyDetails;
import java.util.List;
import java.util.Optional;

/**
 * What a provider said about one LinkedIn company page, in the provider's own words.
 *
 * <p>The port used to answer {@link CapturedCompanyDetails} directly, which threw away the three
 * things the cache needs: the vendor's V2 industry leaf (that record's compact constructor flattens
 * it to the universe's V1 label), its specialties, and the payload itself. They stay here, and
 * {@link #asCapturedDetails()} is the one place the mandate-facing record is built — so a cache hit
 * and a fresh call produce the same row.
 *
 * <p>Lives in {@code enrichment} rather than beside {@code CapturedCompanyDetails} because nothing
 * outside this package consumes it; the record that crosses the seam is still that one.
 */
public record VendorCompanyRecord(String linkedinSlug, String companyName, String industry,
                                  String companyCountry, String companyCity,
                                  Integer employeesInLinkedin, String website, String linkedinUrl,
                                  Integer foundedYear, String about, String logoUrl,
                                  List<String> keywords, String raw) {

    /** A record without even a name is no answer at all. */
    public Optional<CapturedCompanyDetails> asCapturedDetails() {
        if (companyName == null || companyName.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new CapturedCompanyDetails(companyName, industry, companyCountry,
                companyCity, employeesInLinkedin, null, website, linkedinUrl, foundedYear, about,
                logoUrl, null, null));
    }
}
