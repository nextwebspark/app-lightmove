package app.lightmove.api.triagecompany.model;

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
        website = blankToNull(website);
        companyLinkedinUrl = blankToNull(companyLinkedinUrl);
        shortDescription = blankToNull(shortDescription);
        sourceUrl = blankToNull(sourceUrl);
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
