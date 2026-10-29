package app.lightmove.api.enrichment.peoplesearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingContactOutPeopleIndex;
import app.lightmove.api.core.resilience.constant.VendorFailureKind;
import app.lightmove.api.core.resilience.model.VendorCall;
import app.lightmove.api.core.resilience.model.VendorException;
import app.lightmove.api.enrichment.common.model.ContactOutCount;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

/**
 * Strategy's People mode end to end against the recording ContactOut index: the saved filter is what is
 * counted and searched, a page is bought once and answered from the people cache after, and every refusal
 * is decided before anything is spent.
 */
@IntegrationTest
class StrategyPeopleSearchIntegrationTest extends FlowTestSupport {

    private static final String CFO_FILTER = """
            {"filter":{"jobTitles":["CFO","Chief Financial Officer"],"seniorities":["CXO"],
             "jobFunctions":["Finance"],"locations":["Dubai, United Arab Emirates"],"locationRadius":50,
             "industries":["Software Development"],"excludedIndustries":["Banking"],
             "languages":[{"language":"Arabic","proficiencies":["native_or_bilingual"]}],
             "contactTypes":["work_email"]}}""";

    @Autowired private RecordingContactOutPeopleIndex contactOut;
    @Autowired private JdbcTemplate db;

    private String adminToken;

    @BeforeEach
    void resetTheVendor() throws Exception {
        contactOut.clear();
        db.update("DELETE FROM app_lm_vendor_people_search");
        db.update("DELETE FROM app_lm_vendor_person");
        try (InputStream in = getClass().getResourceAsStream("/contactout/people-search.json")) {
            contactOut.searchAnswers(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    @DisplayName("the count leaves out the mandate's declined companies, and the cached search never asks it to")
    void countsTheSavedFilter() throws Exception {
        String projectId = mandate("Count Firm");
        declined(projectId, "Old Rival Group");
        contactOut.countAnswers(new ContactOutCount(1_240L, 300L, 800L, 150L));
        saveFilter(projectId, CFO_FILTER);

        mvc.perform(get(peopleUrl(projectId) + "/count").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offered").value(true))
                .andExpect(jsonPath("$.total").value(1240))
                .andExpect(jsonPath("$.estimatedWorkEmails").value(800));

        Map<String, Object> body = contactOut.counts().getFirst();
        assertThat(body).containsEntry("job_title", List.of("CFO", "Chief Financial Officer"))
                .containsEntry("seniority", List.of("CXO"))
                .containsEntry("location", List.of("Dubai, United Arab Emirates"))
                .containsEntry("location_radius", 50)
                .containsEntry("industry", List.of("Software Development", "NOT Banking"))
                .containsEntry("exclude_companies", List.of("Old Rival Group"))
                .containsEntry("exclude_companies_filter", "current")
                .doesNotContainKeys("data_types", "page", "reveal_info", "name", "keyword");

        search(projectId, 1);
        assertThat(contactOut.searches().getFirst().body())
                .as("a page is cached for every workspace, so one mandate's declines never shape it")
                .doesNotContainKeys("exclude_companies", "exclude_companies_filter");
    }

    @Test
    @DisplayName("an empty filter is counted as nothing and never asked, and searching it is refused")
    void anEmptyFilterIsNotAQuestion() throws Exception {
        String projectId = mandate("Empty Firm");

        mvc.perform(get(peopleUrl(projectId) + "/count").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
        mvc.perform(post(peopleUrl(projectId) + "/search").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PEOPLE_SEARCH_EMPTY_FILTER"));

        assertThat(contactOut.counts()).isEmpty();
        assertThat(contactOut.searches()).isEmpty();
    }

    @Test
    @DisplayName("a page is bought once: asked again, by this mandate or another, it is answered from the cache")
    void aPageIsBoughtOnce() throws Exception {
        String projectId = mandate("Paging Firm");
        saveFilter(projectId, CFO_FILTER);

        JsonNode first = search(projectId, 1);
        assertThat(first.get("billed").asInt()).isEqualTo(1);
        assertThat(first.get("cached").asInt()).isZero();
        assertThat(first.get("total").asLong()).isEqualTo(14);
        JsonNode person = first.get("people").get(0);
        assertThat(person.get("linkedinSlug").asText()).isEqualTo("sample-cfo-12ab");
        assertThat(person.get("companyName").asText()).isEqualTo("Harbour Group");
        assertThat(person.get("companyLinkedinUrl").asText()).isEqualTo("https://www.linkedin.com/company/harbour-group/");
        assertThat(person.get("location").asText()).isEqualTo("Dubai, United Arab Emirates");
        assertThat(person.get("career")).extracting(post -> post.get("title").asText())
                .containsExactly("Chief Executive Officer, Port Division", "Senior Advisor", "Chief Financial Officer");
        assertThat(person.get("education").get(0).get("school").asText()).isEqualTo("Sample University");
        assertThat(person.get("skills")).extracting(JsonNode::asText).containsExactly("Treasury", "M&A");
        assertThat(person.get("held").asBoolean()).isFalse();
        assertThat(person.get("candidateId").isNull()).isTrue();
        JsonNode details = person.get("details");
        assertThat(details.get("headline").asText()).isEqualTo("Group CFO | Board Member");
        assertThat(details.get("links").get(0).get("url").asText()).isEqualTo("https://github.com/samplecfo");
        assertThat(details.get("certifications").get(0).get("title").asText()).isEqualTo("Chartered Accountant");
        assertThat(details.get("contactAvailability").get("phone").asBoolean()).isTrue();
        assertThat(details.get("company").get("website").asText()).isEqualTo("https://harbour.example");
        assertThat(db.queryForObject("select source_record ->> 'headline' from app_lm_vendor_person "
                + "where linkedin_slug = 'sample-cfo-12ab'", String.class)).isEqualTo("Group CFO | Board Member");

        JsonNode again = search(projectId, 1);
        assertThat(again.get("billed").asInt()).isZero();
        assertThat(again.get("cached").asInt()).isEqualTo(1);

        search(projectId, 2);

        String otherProject = mandate("Other Paging Firm");
        saveFilter(otherProject, CFO_FILTER);
        assertThat(search(otherProject, 1).get("billed").asInt()).isZero();

        assertThat(contactOut.searches()).extracting(RecordingContactOutPeopleIndex.SearchAsked::page)
                .containsExactly(1, 2);
        assertThat(contactOut.searches().getFirst().body()).containsEntry("data_types", List.of("work_email"));
        assertThat(db.queryForObject("select count(*) from app_lm_audit_event where target_id = ? "
                + "and event_type = 'PEOPLE_SEARCH_PAGE_FETCHED'", Integer.class, projectId)).isEqualTo(3);
    }

    @Test
    @DisplayName("a ticked person is filed from the cache under their employer, once, and shows as held after")
    void addsTickedPeopleUnderTheirEmployer() throws Exception {
        String projectId = mandate("Adding Firm");
        saveFilter(projectId, CFO_FILTER);
        search(projectId, 1);

        JsonNode addedAnswer = body(mvc.perform(post(peopleUrl(projectId) + "/add")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"linkedinSlugs\":[\"sample-cfo-12ab\",\"never-searched\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added").value(1))
                .andExpect(jsonPath("$.unavailable").value(1))
                .andReturn());

        JsonNode candidate = body(mvc.perform(get("/api/v1/projects/" + projectId + "/candidates")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn()).get("candidates").get(0);
        assertThat(candidate.get("source").asText()).isEqualTo("people_search");
        assertThat(candidate.get("fullName").asText()).isEqualTo("Sample Cfo");
        assertThat(candidate.get("companyName").asText()).isEqualTo("Harbour Group");
        assertThat(candidate.get("linkedinUrl").asText()).isEqualTo("https://www.linkedin.com/in/sample-cfo-12ab/");
        assertThat(db.queryForMap("select p.enriched_by, p.source from app_lm_person p "
                + "join app_lm_project_candidate c on c.person_id = p.id where c.id = ?::uuid", candidate.get("id").asText()))
                .containsEntry("enriched_by", "CONTACTOUT")
                .containsEntry("source", "PEOPLE_SEARCH");
        assertThat(addedAnswer.get("filed").get(0).get("candidateId").asText()).isEqualTo(candidate.get("id").asText());
        Map<String, Object> employer = db.queryForMap("select source, website, industry from "
                + "app_lm_project_triage_company where id = ?", UUID.fromString(candidate.get("triageCompanyId").asText()));
        assertThat(employer).containsEntry("source", "PEOPLE_SEARCH").containsEntry("website", "https://harbour.example");
        assertThat(employer.get("industry")).isNotNull();
        assertThat(db.queryForObject("select count(*) from app_lm_audit_event where target_id = ? "
                + "and event_type = 'CANDIDATE_AI_ENRICH_REQUESTED'", Integer.class, projectId)).isZero();

        mvc.perform(post(peopleUrl(projectId) + "/add").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"linkedinSlugs\":[\"Sample-CFO-12ab\"]}"))
                .andExpect(jsonPath("$.added").value(0))
                .andExpect(jsonPath("$.skipped").value(1));
        JsonNode heldPerson = search(projectId, 1).get("people").get(0);
        assertThat(heldPerson.get("held").asBoolean()).isTrue();
        assertThat(heldPerson.get("candidateId").asText()).isEqualTo(candidate.get("id").asText());
        assertThat(contactOut.searches()).hasSize(1);
    }

    @Test
    @DisplayName("the pages bought read back free on return, and declining an employer neither re-bills them nor keeps its people")
    void readsBackWhatWasBought() throws Exception {
        String projectId = mandate("Returning Firm");
        saveFilter(projectId, CFO_FILTER);
        assertThat(results(projectId).get("pages")).isEmpty();

        search(projectId, 1);
        search(projectId, 2);

        JsonNode pages = results(projectId).get("pages");
        assertThat(pages).extracting(page -> page.get("page").asInt()).containsExactly(1, 2);
        assertThat(pages.get(0).get("billed").asInt()).isZero();
        assertThat(pages.get(0).get("people").get(0).get("linkedinSlug").asText()).isEqualTo("sample-cfo-12ab");

        declined(projectId, "Harbour Group");

        assertThat(results(projectId).get("pages").get(0).get("people")).isEmpty();
        assertThat(search(projectId, 1).get("billed").asInt()).isZero();
        assertThat(contactOut.searches()).hasSize(2);
    }

    @Test
    @DisplayName("a person filed to the shortlist takes their employer there, unless the mandate already holds it elsewhere")
    void filesTheEmployerAtTheChosenStage() throws Exception {
        String projectId = mandate("Staging Firm");
        saveFilter(projectId, CFO_FILTER);
        search(projectId, 1);

        mvc.perform(post(peopleUrl(projectId) + "/add").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"linkedinSlugs\":[\"sample-cfo-12ab\"],\"status\":\"shortlisted\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added").value(1))
                .andExpect(jsonPath("$.elsewhere").value(0));
        assertThat(db.queryForObject("select status from app_lm_project_triage_company where project_id = ?::uuid",
                String.class, projectId)).isEqualTo("SHORTLISTED");

        String otherProject = mandate("Declined Staging Firm");
        declined(otherProject, "Harbour Group");
        saveFilter(otherProject, CFO_FILTER);
        search(otherProject, 1);
        mvc.perform(post(peopleUrl(otherProject) + "/add").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"linkedinSlugs\":[\"sample-cfo-12ab\"],\"status\":\"shortlisted\"}"))
                .andExpect(jsonPath("$.added").value(1))
                .andExpect(jsonPath("$.elsewhere").value(1));
        assertThat(db.queryForObject("select status from app_lm_project_triage_company where project_id = ?::uuid",
                String.class, otherProject)).isEqualTo("DECLINED");
    }

    @Test
    @DisplayName("a people search is saved as its own kind, carrying the people filter and leaving the company one empty")
    void savesAPeopleSearch() throws Exception {
        String projectId = mandate("Saving Firm");
        saveFilter(projectId, CFO_FILTER);

        JsonNode saved = body(mvc.perform(post("/api/v1/projects/" + projectId + "/strategy/searches")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Gulf CFOs\",\"kind\":\"PEOPLE\"}"))
                .andExpect(status().isCreated())
                .andReturn());
        assertThat(saved.get("kind").asText()).isEqualTo("PEOPLE");
        assertThat(saved.get("peopleFilter").get("seniorities")).extracting(JsonNode::asText).containsExactly("CXO");
        assertThat(saved.get("filter").get("industries")).isEmpty();

        saveFilter(projectId, "{\"filter\":{\"seniorities\":[\"VP\"]}}");
        mvc.perform(put("/api/v1/projects/" + projectId + "/strategy/searches/" + saved.get("id").asText() + "/filter")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.peopleFilter.seniorities[0]").value("VP"));

        JsonNode strategy = body(mvc.perform(get("/api/v1/projects/" + projectId + "/strategy")
                        .header("Authorization", "Bearer " + adminToken))
                .andReturn());
        assertThat(strategy.get("peopleFilter").get("seniorities")).extracting(JsonNode::asText).containsExactly("VP");
        assertThat(strategy.get("searches").get(0).get("kind").asText()).isEqualTo("PEOPLE");
    }

    @Test
    @DisplayName("a value ContactOut does not accept, or a pair it refuses together, is refused at the save")
    void refusesWhatTheVendorWould() throws Exception {
        String projectId = mandate("Refusing Firm");

        saveFilterExpecting(projectId, "{\"filter\":{\"seniorities\":[\"Vice President\"]}}", 400);
        saveFilterExpecting(projectId, "{\"filter\":{\"industries\":[\"Computer Software\"]}}", 400);
        saveFilterExpecting(projectId,
                "{\"filter\":{\"recentlyChangedJobs\":true,\"yearsInCurrentRole\":[\"2_4\"]}}", 400);
        saveFilterExpecting(projectId, "{\"filter\":{\"seniorities\":[\"VP\"],\"titleMatch\":\"past\"}}", 200);
    }

    @Test
    @DisplayName("spent credits are told apart, a client seat reaches none of it, and a keyless deployment is not offered")
    void refusalsSpendNothing() throws Exception {
        String projectId = mandate("Access People Firm");
        saveFilter(projectId, CFO_FILTER);

        contactOut.failWith(new VendorException(VendorCall.of("contactout-search", "people-search"),
                VendorFailureKind.QUOTA_EXHAUSTED, null));
        mvc.perform(post(peopleUrl(projectId) + "/search").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PEOPLE_SEARCH_NO_CREDITS"));

        String clientEmail = "client@client-" + domain;
        mvc.perform(post("/api/v1/projects/" + projectId + "/representatives/invitations")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"A Client","position":"Chair","email":"%s"}
                                """.formatted(clientEmail)))
                .andExpect(status().isOk());
        String clientToken = body(mvc.perform(post("/api/v1/onboarding/accept-invitation-signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","fullName":"A Client","password":"%s"}
                                """.formatted(email.latestTokenFor(clientEmail), PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn()).get("accessToken").asText();
        mvc.perform(get(peopleUrl(projectId) + "/count").header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isForbidden());
        mvc.perform(post(peopleUrl(projectId) + "/search").header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isForbidden());

        contactOut.offer(false);
        mvc.perform(get(peopleUrl(projectId) + "/count").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offered").value(false));
        mvc.perform(post(peopleUrl(projectId) + "/search").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PEOPLE_SEARCH_UNAVAILABLE"));
    }

    @Test
    @DisplayName("the sidebar's vocabularies are ContactOut's accepted values, labelled")
    void servesTheVocabulary() throws Exception {
        mandate("Vocabulary Firm");

        mvc.perform(get("/api/v1/companies/people-facets").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seniorities[1].value").value("CXO"))
                .andExpect(jsonPath("$.seniorities[1].label").value("C-suite"))
                .andExpect(jsonPath("$.companySizes[7].value").value("10001"))
                .andExpect(jsonPath("$.industries.length()").value(492));
    }

    @Test
    @DisplayName("the Location box offers countries first, then LinkedIn's spellings of where people on file live")
    void suggestsPlaces() throws Exception {
        String projectId = mandate("Places Firm");
        saveFilter(projectId, CFO_FILTER);
        search(projectId, 1);

        JsonNode places = body(mvc.perform(get("/api/v1/locations/suggest").param("q", "dub")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn()).get("places");
        assertThat(places).extracting(place -> place.get("value").asText())
                .contains("Dubai, United Arab Emirates");
        assertThat(places.get(0).get("kind").asText()).isEqualTo("CITY");

        JsonNode countries = body(mvc.perform(get("/api/v1/locations/suggest").param("q", "United Ara")
                        .header("Authorization", "Bearer " + adminToken))
                .andReturn()).get("places");
        assertThat(countries.get(0).get("value").asText()).isEqualTo("United Arab Emirates");
        assertThat(countries.get(0).get("kind").asText()).isEqualTo("COUNTRY");

        mvc.perform(get("/api/v1/locations/suggest").param("q", "d").header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.places.length()").value(0));
    }

    @Test
    @DisplayName("a page counts towards fair use the first time each workspace reads it, whoever bought it")
    void aPageCountsOncePerWorkspace() throws Exception {
        String projectId = mandate("Metered Firm");
        saveFilter(projectId, CFO_FILTER);

        search(projectId, 1);
        search(projectId, 1);
        results(projectId);

        assertThat(pageUnitsOf(projectId)).containsExactly(1);

        String otherProject = mandate("Second Metered Firm");
        saveFilter(otherProject, CFO_FILTER);
        assertThat(search(otherProject, 1).get("billed").asInt()).isZero();
        assertThat(pageUnitsOf(otherProject)).as("a page from the shared cache is still new to this workspace")
                .containsExactly(1);
    }

    @Test
    @DisplayName("past the fair-use ceiling a new page is refused before ContactOut is asked, a read one is not")
    void aNewPagePastTheCeilingIsRefused() throws Exception {
        String projectId = mandate("Ceiling Firm");
        saveFilter(projectId, CFO_FILTER);
        search(projectId, 1);
        db.update("""
                INSERT INTO app_lm_usage_event (workspace_id, kind, units, est_cost_fils)
                SELECT workspace_id, 'PEOPLE_SEARCH_PAGE', 20000, 0 FROM app_lm_project WHERE id = ?::uuid""",
                projectId);

        search(projectId, 1);
        JsonNode refusal = body(mvc.perform(post(peopleUrl(projectId) + "/search").param("page", "2")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isTooManyRequests())
                .andReturn());

        assertThat(refusal.get("code").asText()).isEqualTo("FAIR_USE_REACHED");
        assertThat(refusal.get("kind").asText()).isEqualTo("PEOPLE_SEARCH_PAGE");
        assertThat(contactOut.searches()).hasSize(1);
        assertThat(db.queryForObject("""
                SELECT count(*) FROM app_lm_audit_event
                WHERE event_type = 'FAIR_USE_REACHED'
                  AND workspace_id = (SELECT workspace_id FROM app_lm_project WHERE id = ?::uuid)""",
                Integer.class, projectId)).isEqualTo(1);
    }

    private List<Integer> pageUnitsOf(String projectId) {
        return db.queryForList("""
                SELECT units FROM app_lm_usage_event
                WHERE kind = 'PEOPLE_SEARCH_PAGE'
                  AND workspace_id = (SELECT workspace_id FROM app_lm_project WHERE id = ?::uuid)""",
                Integer.class, projectId);
    }

    private JsonNode search(String projectId, int page) throws Exception {
        return body(mvc.perform(post(peopleUrl(projectId) + "/search").param("page", Integer.toString(page))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn());
    }

    private JsonNode results(String projectId) throws Exception {
        return body(mvc.perform(get(peopleUrl(projectId) + "/results").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn());
    }

    private void saveFilter(String projectId, String request) throws Exception {
        saveFilterExpecting(projectId, request, 200);
    }

    private void saveFilterExpecting(String projectId, String request, int expectedStatus) throws Exception {
        mvc.perform(put("/api/v1/projects/" + projectId + "/strategy/people/filter")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().is(expectedStatus));
    }

    private void declined(String projectId, String name) throws Exception {
        mvc.perform(post("/api/v1/projects/" + projectId + "/triage/capture")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"" + name + "\",\"status\":\"declined\"}"))
                .andExpect(status().isCreated());
    }

    private static String peopleUrl(String projectId) {
        return "/api/v1/projects/" + projectId + "/strategy/people";
    }

    /** Its own admin per firm, so one test can open two mandates in two workspaces. */
    private String mandate(String firmName) throws Exception {
        String admin = firmName.toLowerCase().replaceAll("[^a-z]", "") + "@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", admin), firmName);
        adminToken = login(admin);
        String clientId = body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customName\":\"Research Client\"}"))
                .andReturn()).get("id").asText();
        return body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"%s\",\"positionTitle\":\"Group CFO\"}".formatted(clientId)))
                .andReturn()).get("id").asText();
    }
}
