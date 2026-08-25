package app.lightmove.api.triagecompany.model;

import java.net.URI;

/**
 * The company fields a mandate supplies itself, when there is no universe row to snapshot from.
 *
 * <p>Every field but the name is optional, and deliberately so: the plugin reads whatever a page
 * happens to publish, and a researcher typing a company in from a conference list may have a name and
 * a country and nothing else. Refusing the row until it is complete would push the consultant back to
 * a spreadsheet, which is the behaviour this whole screen exists to replace.
 *
 * <p>The compact constructor is where "supplied but empty" becomes null. A form posts an untouched
 * text input as {@code ""}, and an empty string stored in a snapshot column renders as a present-but-
 * blank cell and sorts ahead of real values — so the emptiness has to be resolved once, here, rather
 * than at each of the places that later ask whether a field is known.
 *
 * <p>It is also where the two URL fields are made safe to render, and that has to happen server-side
 * rather than in the form: the plugin posts here directly and never sees the form's validation. A
 * consultant types {@code acme.com}, which as an {@code href} is a <i>relative</i> link that navigates
 * inside the SPA instead of to the company, so a bare host gains {@code https://}. Anything that is not
 * then http(s) is dropped rather than stored — {@code javascript:} in an href is the interesting case,
 * and a link the grid refuses to render is better than one it must remember to sanitise at every use.
 */
public record CapturedCompanyDetails(String companyName, String industry, String companyCountry,
                                     String companyCity, Integer numEmployees, Long annualRevenue,
                                     String website, String companyLinkedinUrl, Integer foundedYear,
                                     String shortDescription, String sourceUrl, String note) {

    public CapturedCompanyDetails {
        companyName = companyName == null ? null : companyName.trim();
        industry = blankToNull(industry);
        companyCountry = blankToNull(companyCountry);
        companyCity = blankToNull(companyCity);
        website = webAddressOrNull(website);
        companyLinkedinUrl = webAddressOrNull(companyLinkedinUrl);
        shortDescription = blankToNull(shortDescription);
        sourceUrl = blankToNull(sourceUrl);
    }

    /**
     * A browsable http(s) address, or null. A bare host is promoted rather than refused, because that
     * is what people type and refusing it would lose a field the consultant meant to give us.
     */
    private static String webAddressOrNull(String value) {
        String trimmed = blankToNull(value);
        if (trimmed == null) {
            return null;
        }
        String candidate = trimmed.contains("://") ? trimmed : "https://" + trimmed;
        URI parsed;
        try {
            parsed = URI.create(candidate);
        } catch (IllegalArgumentException notAUri) {
            return null;
        }
        String scheme = parsed.getScheme();
        boolean browsable = scheme != null
                && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                && parsed.getHost() != null;
        return browsable ? candidate : null;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
