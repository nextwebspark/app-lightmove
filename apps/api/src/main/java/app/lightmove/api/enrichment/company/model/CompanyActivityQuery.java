package app.lightmove.api.enrichment.company.model;

import java.util.List;

/**
 * LinkedIn pages by what the company does. {@code industries} are LinkedIn's V2 labels; an empty list
 * places no constraint. {@code size} is what is bought: every hit is billed.
 */
public record CompanyActivityQuery(List<String> words, List<String> countryCodes, List<String> industries,
                                   Integer minEmployees, Integer maxEmployees, List<String> excludedSlugs,
                                   int size) {

    public CompanyActivityQuery {
        words = List.copyOf(words);
        countryCodes = List.copyOf(countryCodes);
        industries = List.copyOf(industries);
        excludedSlugs = List.copyOf(excludedSlugs);
    }
}
