package app.lightmove.api.triagecompany.model;

import static app.lightmove.api.core.text.service.SuppliedText.blankToNull;
import static app.lightmove.api.core.text.service.SuppliedText.browsableUrlOrNull;

/**
 * The company fields a mandate supplies itself, when there is no universe row to snapshot from.
 *
 * <p>Every field but the name is optional, and deliberately so: the plugin reads whatever a page
 * happens to publish, and a researcher typing a company in from a conference list may have a name and
 * a country and nothing else. Refusing the row until it is complete would push the consultant back to
 * a spreadsheet, which is the behaviour this whole screen exists to replace.
 *
 * <p>The compact constructor is where "supplied but empty" becomes null, and where the two URL fields
 * are made safe to render — {@link app.lightmove.api.core.text.service.SuppliedText} holds both rules
 * and the reasoning behind them. It has to happen server-side rather than in the form: the plugin
 * posts here directly and never sees the form's validation.
 *
 * <p>{@code sourceUrl} goes through the same gate even though nothing renders it yet. It is the field
 * the plugin fills from the page it was invoked on, so it is the least trustworthy of the three, and
 * the first screen to show "captured from …" as a link would otherwise inherit a stored XSS from rows
 * written long before it existed.
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
