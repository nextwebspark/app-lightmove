package app.lightmove.api.candidate.model;

import java.time.Instant;
import java.util.List;

/**
 * One vendor's answer for a person as it was sent ({@code record}, JSON), beside the typed reading of it — what
 * {@code app_lm_person_vendor_record} keeps (V229). {@code provider} is the people cache's name for the source.
 */
public record VendorProfileRecord(String provider, String record, ResearchedProfileDetails details,
                                  Instant vendorSeenAt) {

    public static final String CONTACTOUT = "contactout";
    public static final String BRIGHTDATA = "brightdata";
    public static final String HARVESTAPI = "harvestapi";
    public static final String BRIGHTDATA_BUSINESS = "brightdata-business";

    /** Every provider a record is kept from, in the order their readings are laid together: freshest titles first. */
    public static final List<String> PROVIDERS = List.of(CONTACTOUT, BRIGHTDATA, HARVESTAPI, BRIGHTDATA_BUSINESS);

    public VendorProfileRecord {
        details = details == null ? ResearchedProfileDetails.empty() : details;
    }
}
