package app.lightmove.api.enrichment.sourcing.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * A position's whole search as the panel shows it: the words, where people may live (ISO-2 codes; empty for
 * anywhere — each company's own country is added when it is searched), and how many live profiles a company may
 * buy. Cleans itself, as {@link SearchWords} does, whoever wrote it.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SearchCriteria(SearchWords words, List<String> countryCodes, int perCompany) {

    /** The live profiles a company may buy, as the panel offers them. */
    public static final List<Integer> PER_COMPANY_CHOICES = List.of(3, 5, 10);

    public static final int DEFAULT_PER_COMPANY = 3;

    private static final int MAX_COUNTRIES = 12;

    public SearchCriteria {
        words = words == null ? SearchWords.defaultFor(null, null) : words;
        countryCodes = countryCodes == null ? List.of() : countryCodes.stream()
                .filter(Objects::nonNull)
                .map(code -> code.strip().toUpperCase(Locale.ROOT))
                .filter(code -> code.matches("[A-Z]{2}"))
                .distinct()
                .limit(MAX_COUNTRIES)
                .toList();
        perCompany = PER_COMPANY_CHOICES.contains(perCompany) ? perCompany : DEFAULT_PER_COMPANY;
    }

    /** The brief's country and its neighbours; anywhere when the brief names no country. */
    public static List<String> regionOf(String briefCountryCode, List<String> neighbourCountryCodes) {
        return briefCountryCode == null ? List.of()
                : Stream.concat(Stream.of(briefCountryCode), neighbourCountryCodes.stream()).distinct().toList();
    }

    /** The countries one company is searched in: these and its own; anywhere when these are none. */
    public List<String> countriesFor(SourcingCompany company) {
        if (countryCodes.isEmpty() || company.countryCode() == null || countryCodes.contains(company.countryCode())) {
            return countryCodes;
        }
        return Stream.concat(countryCodes.stream(), Stream.of(company.countryCode())).toList();
    }

    public SearchCriteria withWords(SearchWords replaced) {
        return new SearchCriteria(replaced, countryCodes, perCompany);
    }
}
