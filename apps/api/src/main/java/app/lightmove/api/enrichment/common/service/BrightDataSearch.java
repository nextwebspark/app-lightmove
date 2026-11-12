package app.lightmove.api.enrichment.common.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The Search API request envelope shared by the people and company lookups; the filtered field
 * ({@code linkedin_id} vs {@code id}) is the caller's.
 */
public final class BrightDataSearch {

    /**
     * The vendor refuses a logical group of five rules ({@code Filter validation failed}, probed live),
     * so every {@code or} here holds four at most.
     */
    public static final int MAX_RULES_PER_GROUP = 4;

    /** Probed with a thousand slugs and answered in under three seconds; past this the oldest go unexcluded. */
    public static final int MAX_EXCLUDED = 1_000;

    private BrightDataSearch() {
    }

    public static Map<String, Object> exactlyOneWhere(String field, String value) {
        return Map.of("size", 1, "filter", Map.of("name", field, "operator", "=", "value", value));
    }

    /**
     * Find executives' search: current employer {@code companySlug}, one seniority and one function word, none of
     * {@code excludedWords}, minus {@code excludedSlugs}. The employer key is the LinkedIn <i>slug</i> — the numeric id
     * matches nothing; words are single tokens, as a phrase {@code includes} takes 10s and a group times out at 60s.
     * Groups hold at most four rules, hence the nested {@code and} (three levels, the documented depth, probed live).
     */
    public static Map<String, Object> currentEmployeesTitled(String companySlug, List<String> seniorityWords,
                                                            List<String> functionWords, List<String> excludedWords,
                                                            List<String> countryCodes, List<String> excludedSlugs,
                                                            int size) {
        List<Map<String, Object>> rules = new ArrayList<>();
        rules.add(Map.of("name", "current_company_company_id", "operator", "=", "value", companySlug));
        if (!countryCodes.isEmpty()) {
            rules.add(Map.of("name", "country_code", "operator", "in", "value", List.copyOf(countryCodes)));
        }
        List<Map<String, Object>> narrowing = new ArrayList<>();
        anyOf(seniorityWords).ifPresent(narrowing::add);
        anyOf(functionWords).ifPresent(narrowing::add);
        noneOf(excludedWords).ifPresent(narrowing::add);
        if (!excludedSlugs.isEmpty()) {
            narrowing.add(Map.of("name", "linkedin_id", "operator", "not_in",
                    "value", excludedSlugs.stream().limit(MAX_EXCLUDED).toList()));
        }
        if (narrowing.size() == 1) {
            rules.add(narrowing.getFirst());
        } else if (!narrowing.isEmpty()) {
            rules.add(Map.of("operator", "and", "filters", narrowing));
        }
        return Map.of("size", size, "filter", Map.of("operator", "and", "filters", rules));
    }

    private static Optional<Map<String, Object>> anyOf(List<String> words) {
        return titleGroup("or", "includes", words);
    }

    private static Optional<Map<String, Object>> noneOf(List<String> words) {
        return titleGroup("and", "not_includes", words);
    }

    private static Optional<Map<String, Object>> titleGroup(String logical, String operator, List<String> words) {
        List<Map<String, Object>> rules = words.stream()
                .limit(MAX_RULES_PER_GROUP)
                .map(word -> Map.<String, Object>of("name", "position", "operator", operator, "value", word))
                .toList();
        if (rules.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(Map.of("operator", logical, "filters", rules));
    }
}
