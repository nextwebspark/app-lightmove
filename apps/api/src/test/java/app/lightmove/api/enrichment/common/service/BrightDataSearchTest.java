package app.lightmove.api.enrichment.common.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The Find executives filter, held to what the vendor enforces: the employer keyed by slug, a country
 * rule and exclusions only when there is something to send, no logical group of more than four
 * rules, and no deeper than three levels.
 */
class BrightDataSearchTest {

    @Test
    @DisplayName("keys the employer on its slug; titles and exclusions nest in one and-group, never five rules in one")
    void buildsTheEmployerTitleAndExclusionFilter() {
        Map<String, Object> body = BrightDataSearch.currentEmployeesTitled("dp-world",
                List.of("Chief", "Head"), List.of("Finance", "Financial", "CFO"), List.of("Assistant", "Accountant"),
                List.of("AE", "SA"), List.of("risalat-rehman", "bumal"), 10);

        assertThat(body.get("size")).isEqualTo(10);
        List<Map<String, Object>> rules = rulesOf(body.get("filter"));
        assertThat(rules).hasSize(3);
        assertThat(rules.get(0)).containsEntry("name", "current_company_company_id")
                .containsEntry("operator", "=").containsEntry("value", "dp-world");
        assertThat(rules.get(1)).containsEntry("name", "country_code").containsEntry("operator", "in")
                .containsEntry("value", List.of("AE", "SA"));
        List<Map<String, Object>> narrowing = rulesOf(rules.get(2));
        assertThat(narrowing).hasSize(4);
        assertThat(alternativesOf(narrowing.get(0))).extracting(rule -> rule.get("value"))
                .containsExactly("Chief", "Head");
        assertThat(alternativesOf(narrowing.get(1))).extracting(rule -> rule.get("value"))
                .containsExactly("Finance", "Financial", "CFO");
        assertThat(alternativesOf(narrowing.get(1))).allSatisfy(rule ->
                assertThat(rule).containsEntry("name", "position").containsEntry("operator", "includes"));
        List<Map<String, Object>> excluded = rulesOf(narrowing.get(2));
        assertThat(excluded).extracting(rule -> rule.get("value")).containsExactly("Assistant", "Accountant");
        assertThat(excluded).allSatisfy(rule ->
                assertThat(rule).containsEntry("name", "position").containsEntry("operator", "not_includes"));
        assertThat(narrowing.get(3)).containsEntry("name", "linkedin_id").containsEntry("operator", "not_in")
                .containsEntry("value", List.of("risalat-rehman", "bumal"));
    }

    @Test
    @DisplayName("no country, no function words and nothing to exclude send none of those — the seniority group stands alone")
    void leavesOutWhatWasNotAsked() {
        List<Map<String, Object>> rules = rulesOf(BrightDataSearch.currentEmployeesTitled("acme",
                List.of("Chief", "Managing"), List.of(), List.of(), List.of(), List.of(), 5).get("filter"));

        assertThat(rules).hasSize(2);
        assertThat(rules.get(1)).containsEntry("operator", "or");
    }

    @Test
    @DisplayName("a fifth word is dropped, and the exclusion stops at the length the vendor was probed with")
    void capsGroupsAndExclusion() {
        List<String> tooMany = IntStream.range(0, BrightDataSearch.MAX_EXCLUDED + 5).mapToObj(i -> "p" + i).toList();
        List<Map<String, Object>> rules = rulesOf(BrightDataSearch.currentEmployeesTitled("acme",
                List.of("Chief", "Head", "Director", "VP", "President"), List.of("Finance"), List.of(), List.of(),
                tooMany, 5).get("filter"));

        List<Map<String, Object>> narrowing = rulesOf(rules.get(1));
        assertThat(alternativesOf(narrowing.get(0))).hasSize(BrightDataSearch.MAX_RULES_PER_GROUP);
        assertThat((List<?>) narrowing.get(2).get("value")).hasSize(BrightDataSearch.MAX_EXCLUDED);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rulesOf(Object group) {
        Map<String, Object> filter = (Map<String, Object>) group;
        assertThat(filter).containsEntry("operator", "and");
        return (List<Map<String, Object>>) filter.get("filters");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> alternativesOf(Map<String, Object> group) {
        assertThat(group).containsEntry("operator", "or");
        return (List<Map<String, Object>>) group.get("filters");
    }
}
