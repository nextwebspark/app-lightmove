package app.lightmove.api.triagecompany.model;

import static app.lightmove.api.core.text.service.SuppliedText.blankToNull;
import static app.lightmove.api.core.text.service.SuppliedText.browsableUrlOrNull;

/**
 * The company fields a mandate supplies itself, when there is no universe row to snapshot from. Every
 * field but the name is optional: the plugin reads whatever a page publishes, and refusing an
 * incomplete row would push the consultant back to a spreadsheet.
 *
 * <p>The compact constructor is where "supplied but empty" becomes null and where every URL field is
 * made safe to render ({@link app.lightmove.api.core.text.service.SuppliedText}). It has to happen
 * server-side: the plugin posts here directly and never sees the form's validation.
 * {@code sourceUrl} goes through the same gate though nothing renders it yet, so the first screen to
 * show "captured from …" as a link does not inherit a stored XSS from older rows.
 */
public record CapturedCompanyDetails(String companyName, String industry, String companyCountry,
                                     String companyCity, Integer numEmployees, Long annualRevenue,
                                     String website, String companyLinkedinUrl, Integer foundedYear,
                                     String shortDescription, String logoUrl, String sourceUrl,
                                     String note) {

    public CapturedCompanyDetails {
        companyName = companyName == null ? null : companyName.trim();
        industry = blankToNull(industry);
        companyCountry = blankToNull(companyCountry);
        companyCity = blankToNull(companyCity);
        website = browsableUrlOrNull(website);
        companyLinkedinUrl = browsableUrlOrNull(companyLinkedinUrl);
        shortDescription = blankToNull(shortDescription);
        logoUrl = browsableUrlOrNull(logoUrl);
        sourceUrl = browsableUrlOrNull(sourceUrl);
    }
}
