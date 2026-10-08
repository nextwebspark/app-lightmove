package app.lightmove.api.enrichment.company.model;

import java.util.List;

/**
 * LinkedIn pages whose specialties or about text name one of {@code words} — what a company does, not
 * what it is called — in one of {@code countryCodes} and filed under one of {@code industries} (LinkedIn's
 * own V2 labels; either axis anywhere when empty), between the two headcounts where given, leaving out
 * {@code excludedSlugs}. {@code size} is what is bought: every hit is billed.
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
