package app.lightmove.api.candidate.model;

import java.time.Instant;

/**
 * One vendor's answer for a person as it was sent ({@code record}, JSON), beside the typed reading of it — what
 * {@code app_lm_person_vendor_record} keeps (V229). {@code provider} is the people cache's name for the source.
 */
public record VendorProfileRecord(String provider, String record, ResearchedProfileDetails details,
                                  Instant vendorSeenAt) {

    public VendorProfileRecord {
        details = details == null ? ResearchedProfileDetails.empty() : details;
    }
}
