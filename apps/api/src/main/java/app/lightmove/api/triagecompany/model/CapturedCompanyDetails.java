package app.lightmove.api.triagecompany.model;

import static app.lightmove.api.core.text.service.TextUtils.blankToNull;
import static app.lightmove.api.core.text.service.TextUtils.browsableUrlOrNull;

import app.lightmove.api.common.industry.service.Industries;
import app.lightmove.api.common.location.service.Countries;

/**
 * Company fields a mandate supplies itself; only the name is required. The compact constructor makes
 * every URL safe to render server-side — the plugin posts directly — {@code sourceUrl} included, so
 * the first screen to link it inherits no stored XSS.
 */
public record CapturedCompanyDetails(String companyName, String industry, String companyCountry,
                                     String companyCity, Integer numEmployees, Long annualRevenue,
                                     String website, String companyLinkedinUrl, Integer foundedYear,
                                     String shortDescription, String logoUrl, String sourceUrl,
                                     String note) {

    public CapturedCompanyDetails {
        companyName = companyName == null ? null : companyName.trim();
        // Every door builds this record, so country and industry spellings are settled once, here.
        industry = Industries.nameOf(industry);
        companyCountry = Countries.nameOf(blankToNull(companyCountry));
        companyCity = Countries.cityOf(blankToNull(companyCity));
        website = browsableUrlOrNull(website);
        companyLinkedinUrl = browsableUrlOrNull(companyLinkedinUrl);
        shortDescription = blankToNull(shortDescription);
        logoUrl = browsableUrlOrNull(logoUrl);
        sourceUrl = browsableUrlOrNull(sourceUrl);
    }
}
