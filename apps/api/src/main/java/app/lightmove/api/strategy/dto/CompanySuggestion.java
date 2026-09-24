package app.lightmove.api.strategy.dto;

import app.lightmove.api.strategy.model.CompanyRow;

/**
 * One company offered by a picker's typeahead — enough to recognise it and to store the snapshot the
 * caller will keep, and nothing more. {@code apolloAccountId} is the identity every stored reference
 * uses.
 */
public record CompanySuggestion(String apolloAccountId, String companyName, String industry,
                                 String companyCity, String companyCountry, String website,
                                 String logoUrl, Integer numEmployees) {

    public static CompanySuggestion of(CompanyRow row) {
        return new CompanySuggestion(row.apolloAccountId(), row.companyName(), row.industry(),
                row.companyCity(), row.companyCountry(), row.website(), row.logoUrl(),
                row.numEmployees());
    }
}
