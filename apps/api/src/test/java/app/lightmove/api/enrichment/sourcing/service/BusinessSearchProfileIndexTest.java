package app.lightmove.api.enrichment.sourcing.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.enrichment.candidate.model.BrightDataPeopleHits;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson;
import app.lightmove.api.enrichment.sourcing.constant.SearchLevel;
import app.lightmove.api.enrichment.sourcing.model.SearchWords;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * The profile index's one query a company, and its answer read into the record shape the run judges and
 * files: a title still on record is the role held now, and a record without one is left for the live
 * index to confirm.
 */
class BusinessSearchProfileIndexTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final YearMonth NOVEMBER_2026 = YearMonth.of(2026, 11);
    private static final SearchWords REAL_ESTATE = new SearchWords(List.of(SearchLevel.C_LEVEL),
            List.of("Real Estate", "Development"), List.of("Managing Director"), List.of(), List.of("Assistant"),
            List.of(), null);

    @Test
    @DisplayName("keys the company on its slug and the role group on the function's words, never a level word")
    void theQuery() {
        Map<String, Object> body = BusinessSearchProfileIndex.searchBody("harbour-group", REAL_ESTATE,
                List.of("AE", "SA"), List.of("held-one"), 250);

        assertThat(body).containsEntry("mode", "ludicrous").containsEntry("view", "full").containsEntry("limit", 100);
        assertThat(conditionsOf(body)).containsExactly(
                Map.of("equals", Map.of("current_company_id", "harbour-group")),
                Map.of("text", Map.of("role", Map.of("value", "Real Estate Development", "mode", "any"))),
                Map.of("in", Map.of("country_code", List.of("AE", "SA"))),
                Map.of("not", Map.of("in", Map.of("linkedin_person_id", List.of("held-one")))));
    }

    @Test
    @DisplayName("no countries and nobody to leave out send neither")
    void leavesOutWhatWasNotAsked() {
        Map<String, Object> body = BusinessSearchProfileIndex.searchBody("harbour-group", REAL_ESTATE, List.of(),
                List.of(), 100);

        assertThat(conditionsOf(body)).hasSize(2);
    }

    @Test
    @DisplayName("a title on record is the role held now, begun its tenure ago, and the record is kept whole beside it")
    void aTitledRecord() {
        BrightDataPeopleHits hits = BusinessSearchRecords.toHits(JSON.readTree("""
                {"documents":[{"data":{"linkedin_person_id":"sample-director","linkedin_num_id":"12345",
                  "name":"Sample Director","current_title":"Director of Development","current_tenure_months":20,
                  "current_company_id":"harbour-group","current_company_name":"Harbour Group",
                  "location":"Dubai, United Arab Emirates","country_code":"AE","avatar":"https://cdn.example/a.jpg",
                  "languages":["English","Arabic"]}}],
                 "meta":{"matched":42}}"""), NOVEMBER_2026, JSON);

        assertThat(hits.totalHits()).isEqualTo(42);
        BrightDataPerson person = hits.hits().getFirst();
        assertThat(person.linkedinId()).isEqualTo("sample-director");
        assertThat(person.currentCompany().companyId()).isEqualTo("harbour-group");
        assertThat(person.countryCode()).isEqualTo("AE");
        assertThat(person.usableAvatarUrl()).isEqualTo("https://cdn.example/a.jpg");
        assertThat(person.experience()).singleElement().satisfies(role -> {
            assertThat(role.title()).isEqualTo("Director of Development");
            assertThat(role.startDate()).isEqualTo("Mar 2025");
            assertThat(role.endDate()).isEqualTo("Present");
        });
        assertThat(SourcedHitRanking.currentRole(person)).isEqualTo("Director of Development");
        assertThat(hits.sourceRecordOf(0)).contains("\"current_tenure_months\":20");
    }

    @Test
    @DisplayName("a record whose title the index dropped holds no role; one with no profile is no record at all")
    void untitledAndUnkeyedRecords() {
        BrightDataPeopleHits hits = BusinessSearchRecords.toHits(JSON.readTree("""
                {"documents":[
                  {"data":{"linkedin_person_id":"untitled","name":"Untitled Person","current_title":null,
                           "current_tenure_months":"7","current_company_id":"harbour-group","avatar":null}},
                  {"data":{"name":"No Profile","current_title":"Head of Development"}}]}"""), NOVEMBER_2026, JSON);

        assertThat(hits.hits()).singleElement().satisfies(person -> {
            assertThat(person.linkedinId()).isEqualTo("untitled");
            assertThat(person.experience()).isEmpty();
            assertThat(SourcedHitRanking.currentRole(person)).isEmpty();
            assertThat(person.usableAvatarUrl()).isNull();
        });
        assertThat(hits.totalHits()).isNull();
        assertThat(BusinessSearchRecords.toHits(null, NOVEMBER_2026, JSON).hits()).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> conditionsOf(Map<String, Object> body) {
        return (List<Map<String, Object>>) ((Map<String, Object>) body.get("query")).get("and");
    }
}
