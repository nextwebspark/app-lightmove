package app.lightmove.api.enrichment.sourcing.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.candidate.constant.EnrichmentVendor;
import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.candidate.model.EnrichedProfile;
import app.lightmove.api.enrichment.candidate.model.BrightDataPeopleHits;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson;
import app.lightmove.api.enrichment.candidate.service.BrightDataPersonProfiles;
import app.lightmove.api.enrichment.sourcing.model.SearchedEmployer;
import app.lightmove.api.enrichment.sourcing.model.SourcingSpec;
import app.lightmove.api.enrichment.common.service.ContactOutPeopleRecords;
import app.lightmove.api.enrichment.common.service.ContactOutPeopleRecords.ContactOutEmployerKey;
import app.lightmove.api.enrichment.common.service.ContactOutPeopleRecords.ContactOutSearchAnswer;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** The run's words as ContactOut's Boolean title, and its answer read into the record shape the run files from. */
class ContactOutPeopleSearchTest {

    private static final SourcingSpec CFO_WORDS = SourcingSpec.of(List.of("Chief", "CFO"),
            List.of("Finance", "Financial"), List.of("Assistant"), null);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final SearchedEmployer HARBOUR = new SearchedEmployer("harbour-group", "harbour.example",
            "Harbour Group");
    private static final ContactOutEmployerKey HARBOUR_KEY = new ContactOutEmployerKey("harbour-group", "Harbour Group");

    @Test
    @DisplayName("the company is its domain, current titles only, the codes where the role is")
    void buildsTheFilter() {
        Map<String, Object> filter = ContactOutPeopleSearch.baseFilter(HARBOUR, CFO_WORDS, List.of("AE", "SA"))
                .orElseThrow();

        assertThat(filter.get("domain")).isEqualTo(List.of("harbour.example"));
        assertThat(filter).doesNotContainKeys("company", "job_title", "page_size");
        assertThat(filter.get("current_titles_only")).isEqualTo(true);
        assertThat(filter.get("exclude_job_titles")).isEqualTo(List.of("Assistant"));
        assertThat(filter).doesNotContainKey("location");
        assertThat(filter.get("current_work_location")).isEqualTo(List.of("United Arab Emirates", "Saudi Arabia"));
    }

    @Test
    @DisplayName("without a domain the company is searched by name, and without either it is not searched")
    void fallsBackToTheName() {
        Map<String, Object> byName = ContactOutPeopleSearch.baseFilter(
                new SearchedEmployer("harbour-group", null, " Harbour Group "), CFO_WORDS, List.of()).orElseThrow();

        assertThat(byName.get("company")).isEqualTo(List.of("Harbour Group"));
        assertThat(byName).doesNotContainKeys("domain", "current_work_location");
        assertThat(ContactOutPeopleSearch.baseFilter(new SearchedEmployer("harbour-group", null, null), CFO_WORDS,
                List.of())).isEmpty();
    }

    @Test
    @DisplayName("the seat's own titles are asked first, then the rest without them, so no one is bought twice")
    void asksTheSeatsTitlesFirst() {
        SourcingSpec wider = SourcingSpec.of(List.of("Chief", "CFO", "Head", "Director"),
                List.of("Finance", "Financial"), List.of(), null);

        assertThat(ContactOutPeopleSearch.titleTiers(wider, Seniority.C_SUITE)).containsExactly(
                "(Chief OR CFO) AND (Finance OR Financial)",
                "((Head OR Director) AND (Finance OR Financial)) NOT (Chief OR CFO)");
        assertThat(ContactOutPeopleSearch.titleTiers(wider, Seniority.N_MINUS_1)).containsExactly(
                "(Head OR Director) AND (Finance OR Financial)",
                "((Chief OR CFO) AND (Finance OR Financial)) NOT (Head OR Director)");
        assertThat(ContactOutPeopleSearch.titleTiers(CFO_WORDS, Seniority.C_SUITE))
                .containsExactly("(Chief OR CFO) AND (Finance OR Financial)");
        assertThat(ContactOutPeopleSearch.titleTiers(SourcingSpec.of(List.of("Manager", "Senior"), List.of("Finance"),
                List.of(), null), Seniority.C_SUITE)).containsExactly("(Manager OR Senior) AND (Finance)");
    }

    @Test
    @DisplayName("a proposed word that is an operator or carries a bracket never reaches the query")
    void dropsWordsThatWouldChangeTheQuery() {
        SourcingSpec hostile = new SourcingSpec(List.of("Chief", "OR", "Head) OR (Intern"),
                List.of("Finance", "not", "\"CFO\""), List.of(), null);

        assertThat(ContactOutPeopleSearch.titleTiers(hostile, Seniority.C_SUITE))
                .containsExactly("(Chief) AND (Finance)");
        assertThat(ContactOutPeopleSearch.titleTiers(new SourcingSpec(List.of("AND"), List.of("("), List.of(), null),
                Seniority.C_SUITE)).isEmpty();
    }

    @Test
    @DisplayName("an abbreviation Bright Data could not search is asked again as a whole word")
    void asksTheAbbreviationBack() {
        SourcingSpec technology = SourcingSpec.of(List.of("Head"), List.of("Technology", "Digital", "IT"), List.of(),
                null);

        assertThat(technology.functionWords()).doesNotContain("IT");
        assertThat(ContactOutPeopleSearch.titleTiers(technology, Seniority.N_MINUS_1))
                .containsExactly("(Head) AND (Technology OR Digital OR CIO OR CTO OR IT)");
    }

    @Test
    @DisplayName("a hit is filed under the searched company, its role there first, and credited to ContactOut")
    void readsAnAnswer() throws Exception {
        BrightDataPeopleHits hits = ContactOutPeopleRecords.toHits(fixture(), HARBOUR_KEY, JSON);

        assertThat(hits.totalHits()).isEqualTo(14);
        assertThat(hits.hits()).singleElement().satisfies(person -> {
            assertThat(person.linkedinId()).isEqualTo("sample-cfo-12ab");
            assertThat(person.position()).isEqualTo("Chief Executive Officer, Port Division");
            assertThat(person.countryCode()).isEqualTo("AE");
            assertThat(person.currentCompany().companyId()).isEqualTo("harbour-group");
            assertThat(person.experience().getFirst().title()).isEqualTo("Chief Executive Officer, Port Division");
            assertThat(person.experience().getFirst().startDate()).isEqualTo("Feb 2026");
            assertThat(person.experience().getFirst().endDate()).isEqualTo("Present");
            assertThat(SourcedHitRanking.currentRoleTitle(person)).isEqualTo("Chief Executive Officer, Port Division");
        });

        BrightDataPerson person = hits.hits().getFirst();
        EnrichedProfile profile = BrightDataPersonProfiles.toEnrichedProfile(person, EnrichmentVendor.CONTACTOUT);
        assertThat(profile.vendor()).isEqualTo(EnrichmentVendor.CONTACTOUT);
        assertThat(profile.title()).isEqualTo("Chief Executive Officer, Port Division");
        assertThat(profile.locationCity()).isEqualTo("Dubai");
        assertThat(profile.career()).extracting(entry -> entry.title())
                .containsExactly("Chief Executive Officer, Port Division", "Senior Advisor", "Chief Financial Officer");
        assertThat(profile.education()).singleElement().satisfies(school ->
                assertThat(school.period()).isEqualTo("2005 – 2007"));
    }

    @Test
    @DisplayName("a people-first answer files each person under the employer their own profile names")
    void filesAPeopleFirstHitUnderItsOwnEmployer() throws Exception {
        BrightDataPerson person = ContactOutPeopleRecords.toHits(fixture(), JSON).hits().getFirst();

        assertThat(person.currentCompany().companyId()).isEqualTo("harbour-group");
        assertThat(person.currentCompany().name()).isEqualTo("Harbour Group");
        assertThat(person.experience().getFirst().title()).isEqualTo("Chief Executive Officer, Port Division");
    }

    @Test
    @DisplayName("an answer with nobody in it sends profiles as an empty array, and reads as no hits")
    void readsAnEmptyAnswer() {
        ContactOutSearchAnswer empty = JSON.readValue("""
                {"status_code":200,"metadata":{"page":1,"page_size":25,"total_results":0},"profiles":[]}""",
                ContactOutSearchAnswer.class);

        BrightDataPeopleHits hits = ContactOutPeopleRecords.toHits(empty, HARBOUR_KEY, JSON);

        assertThat(hits.hits()).isEmpty();
        assertThat(hits.totalHits()).isZero();
    }

    private static ContactOutSearchAnswer fixture() throws Exception {
        try (InputStream in = ContactOutPeopleSearchTest.class.getResourceAsStream("/contactout/people-search.json")) {
            return JSON.readValue(in, ContactOutSearchAnswer.class);
        }
    }
}
