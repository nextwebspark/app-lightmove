package app.lightmove.api.notetaker.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The value a suggestion proposes. Which parts are set depends on its kind: {@code text} for a notice period, a
 * motivation, an expected package or a task; {@code city}/{@code country} for a location; the currency and the three
 * figures (annual, whole units) for a package, with {@code working} saying how monthly figures were annualised.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SuggestedValue(String text, String city, String country, String currency, Long baseSalary, Long bonus,
                             Long allowances, String working) {

    /** No package figure in any currency this market quotes reaches ten billion units; one that does is misread. */
    public static final double LARGEST_FIGURE = 1e10;

    public static SuggestedValue text(String text) {
        return new SuggestedValue(text, null, null, null, null, null, null, null);
    }

    public static SuggestedValue place(String city, String country) {
        return new SuggestedValue(null, city, country, null, null, null, null, null);
    }

    public static SuggestedValue pay(String currency, Long baseSalary, Long bonus, Long allowances, String working) {
        return new SuggestedValue(null, null, null, currency, baseSalary, bonus, allowances, working);
    }
}
