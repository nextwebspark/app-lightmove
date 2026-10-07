package app.lightmove.api.assistant.tool;

import app.lightmove.api.strategy.model.CompanyExclusion;
import app.lightmove.api.strategy.model.CompanyScope;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * A market search an answer ran, in the universe's own spellings, so the next search can be built from
 * it by changing one axis. A search by company name is a lookup, not a market, and is never one.
 */
public record MarketAsk(List<String> countries, List<String> industries, String keyword,
                        Long minEmployees, Long maxEmployees) {

    public MarketAsk {
        countries = countries == null ? List.of() : List.copyOf(countries);
        industries = industries == null ? List.of() : List.copyOf(industries);
        keyword = keyword == null || keyword.isBlank() ? null : keyword.strip();
    }

    /** The search's companies the mandate has not filed and the client has not ruled off limits. */
    public CompanyScope newCompaniesScope(List<String> offLimitsAccountIds, CompanyExclusion filed) {
        CompanyScope asked = MarketQuery.scopeOf(countries, industries, keyword, null, minEmployees, maxEmployees);
        return new CompanyScope(asked.industries(), asked.keywords(), asked.marketSegments(), asked.countries(),
                asked.employeeBands(), asked.revenueBands(), asked.employeeRange(), asked.revenueRange(),
                offLimitsAccountIds, filed, null);
    }

    public boolean hasRoomFor(List<String> axis) {
        return axis.size() < MarketQuery.MAX_VALUES_PER_AXIS;
    }

    public MarketAsk withIndustry(String industry) {
        return new MarketAsk(countries, append(industries, industry), keyword, minEmployees, maxEmployees);
    }

    public MarketAsk withoutIndustry(String industry) {
        return new MarketAsk(countries, without(industries, industry), keyword, minEmployees, maxEmployees);
    }

    public MarketAsk withCountry(String country) {
        return new MarketAsk(append(countries, country), industries, keyword, minEmployees, maxEmployees);
    }

    public MarketAsk onlyIn(String country) {
        return new MarketAsk(List.of(country), industries, keyword, minEmployees, maxEmployees);
    }

    public MarketAsk withoutKeyword() {
        return new MarketAsk(countries, industries, null, minEmployees, maxEmployees);
    }

    public MarketAsk ofAnySize() {
        return new MarketAsk(countries, industries, keyword, null, null);
    }

    public MarketAsk withMinEmployees(long fewest) {
        return new MarketAsk(countries, industries, keyword, fewest, maxEmployees);
    }

    /** "Find retail and hospitality companies describing themselves as "grocery" in United Arab Emirates with at least 1,000 staff". */
    public String asQuestion() {
        StringBuilder question = new StringBuilder("Find ");
        if (!industries.isEmpty()) {
            question.append(String.join(" and ", industries)).append(' ');
        }
        question.append("companies");
        if (keyword != null) {
            question.append(" describing themselves as \"").append(keyword).append('"');
        }
        if (!countries.isEmpty()) {
            question.append(" in ").append(String.join(" and ", countries));
        }
        String staff = staffOf(minEmployees, maxEmployees);
        if (staff != null) {
            question.append(" with ").append(staff);
        }
        return question.toString();
    }

    private static String staffOf(Long min, Long max) {
        if (min != null && max != null) {
            return String.format(Locale.ROOT, "%,d–%,d staff", min, max);
        }
        if (min != null) {
            return String.format(Locale.ROOT, "at least %,d staff", min);
        }
        return max == null ? null : String.format(Locale.ROOT, "up to %,d staff", max);
    }

    private static List<String> append(List<String> values, String value) {
        return Stream.concat(values.stream(), Stream.of(value)).distinct().toList();
    }

    private static List<String> without(List<String> values, String value) {
        return values.stream().filter(held -> !held.equalsIgnoreCase(value)).toList();
    }
}
