package app.lightmove.api.companydiscovery.model;

import java.util.List;

/**
 * What the model replies, before anything has been checked. Deliberately the narrowest shape the
 * feature can work from: there is no field here for a headcount, a country or an industry, so a
 * model that volunteers one has nowhere to put it and the value is dropped at the binding rather
 * than somewhere downstream where a reader might trust it.
 */
public record ModelDiscoveryAnswer(List<Proposed> companies) {

    public record Proposed(String companyName, String linkedinUrl, String websiteUrl,
                           String sourceUrl, String reason, Integer fit) {}
}
