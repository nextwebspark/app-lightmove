package app.lightmove.api.strategy.dto;

/**
 * One company offered by a picker's typeahead — enough to recognise it and to store the snapshot the
 * caller will keep, and nothing more. {@code apolloAccountId} is the identity every stored reference
 * uses.
 */
public record CompanySuggestion(String apolloAccountId, String companyName, String industry,
                                 String companyCity, String companyCountry, String website,
                                 String logoUrl, Integer numEmployees) {}
