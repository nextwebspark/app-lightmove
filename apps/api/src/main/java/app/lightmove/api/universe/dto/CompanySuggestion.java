package app.lightmove.api.universe.dto;

import app.lightmove.api.universe.model.CompanyRow;

/** One typeahead company: enough to recognise it and to store its snapshot. */
public record CompanySuggestion(String apolloAccountId, String companyName, String industry,
                                 String companyCity, String companyCountry, String website,
                                 String logoUrl, Integer numEmployees) {

    public static CompanySuggestion of(CompanyRow row) {
        return new CompanySuggestion(row.apolloAccountId(), row.companyName(), row.industry(),
                row.companyCity(), row.companyCountry(), row.website(), row.logoUrl(),
                row.numEmployees());
    }
}
