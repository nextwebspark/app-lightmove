package app.lightmove.api.enrichment.sourcing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingPeopleSearch;
import app.lightmove.api.StubChatModel;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson.BrightDataExperience;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

/**
 * Find executives end to end: the POST answers 202 with the run, the worker runs inline (see
 * {@code SynchronousAuditWrites}) against the recording people search and the stub model, and the
 * picks land as AI-sourced executives at their companies.
 *
 * <p>The stub answers every prompt of a run with one document, so it carries every shape the run
 * asks for: the spec's words, the rerank's picks, and the deep enrichment's assessment and
 * nationality. Each call binds the half it asked for.
 */
@IntegrationTest
class ExecutiveSourcingIntegrationTest extends FlowTestSupport {

    private static final String EVERY_ANSWER = """
            {"seniorityWords":["Chief","Head"],"functionWords":["Finance","Financial"],
             "excludedWords":["Assistant","Office"],
             "roleSummary":"The group's finance chief.",
             "picks":[{"hit":1,"score":9,"reason":"Holds the seat"},{"hit":2,"score":7,"reason":"One step below"},
                      {"hit":3,"score":5,"reason":"Nearest available"}],
             "note":null,
             "category":"Emirati","confidence":"high","evidence_for":[],"evidence_against":[],"rule_applied":"none",
             "gender":"male","yearsExperience":20,"seniority":"C-Suite",
             "summary":"A proven finance leader.",
             "technical":{"score":8,"positives":[],"negatives":[]},
             "behavioural":{"score":7,"positives":[],"negatives":[]}}""";

    @Autowired private RecordingPeopleSearch peopleSearch;
    @Autowired private StubChatModel model;
    @Autowired private JdbcTemplate db;

    private String adminToken;

    @BeforeEach
    void resetTheVendor() {
        peopleSearch.clear();
        model.answerWith(EVERY_ANSWER);
    }

    @AfterEach
    void resetTheModel() {
        model.reset();
    }

    @Test
    @DisplayName("a run files the model's picks at or above the score floor, researched from the hit, and enriches them")
    void aRunFilesThePicks() throws Exception {
        String projectId = mandate("Sourcing Firm");
        String dpWorld = company(projectId, "DP World", "https://www.linkedin.com/company/dp-world/");
        String noPage = company(projectId, "Hand Typed Co", null);
        peopleSearch.answerWith("dp-world", List.of(
                person("risalat-rehman", "Risalat Rehman", "Chief Financial Officer at DP World Jeddah"),
                person("group-director", "Group Director", "Group Director - Financial Accounts"),
                person("third-person", "Third Person", "Head of Finance Transformation")));

        JsonNode started = body(mvc.perform(post(sourcingUrl(projectId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"triageCompanyIds\":[]}"))
                .andExpect(status().isAccepted())
                .andReturn());
        String runId = started.get("id").asText();

        JsonNode run = runOf(projectId, runId);
        assertThat(run.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(run.get("companiesTotal").asInt()).isEqualTo(2);
        assertThat(run.get("companiesDone").asInt()).isEqualTo(2);
        assertThat(run.get("executivesFiled").asInt()).isEqualTo(2);
        assertThat(run.get("vendorHits").asInt()).isEqualTo(3);
        JsonNode outcomes = run.get("outcomes");
        assertThat(outcomes).hasSize(2);
        JsonNode filed = outcomeFor(outcomes, dpWorld);
        assertThat(filed.get("outcome").asText()).isEqualTo("FILED");
        assertThat(filed.get("filed").asInt()).isEqualTo(2);
        assertThat(filed.get("picks").get(0).get("name").asText()).isEqualTo("Risalat Rehman");
        assertThat(filed.get("picks").get(0).get("reason").asText()).isEqualTo("Holds the seat");
        assertThat(filed.get("picks")).hasSize(2);
        assertThat(filed.get("seen").asInt()).isEqualTo(3);
        assertThat(filed.get("matched").asLong()).isEqualTo(3);
        assertThat(outcomeFor(outcomes, noPage).get("outcome").asText()).isEqualTo("NO_LINKEDIN_PAGE");
        assertThat(run.get("searchedFor").get("excludedWords")).extracting(JsonNode::asText)
                .containsExactly("Assistant");

        assertThat(peopleSearch.searches()).hasSize(1);
        RecordingPeopleSearch.Asked search = peopleSearch.searches().getFirst();
        assertThat(search.companySlug()).isEqualTo("dp-world");
        assertThat(search.seniorityWords()).containsExactly("Chief", "Head");
        assertThat(search.functionWords()).containsExactly("Finance", "Financial");
        assertThat(search.excludedWords()).containsExactly("Assistant");
        assertThat(search.countryCodes()).isEmpty();

        JsonNode people = candidatesOf(projectId);
        assertThat(people).hasSize(2);
        JsonNode cfo = people.get(0).get("fullName").asText().equals("Risalat Rehman") ? people.get(0) : people.get(1);
        assertThat(cfo.get("source").asText()).isEqualTo("ai_sourced");
        assertThat(cfo.get("triageCompanyId").asText()).isEqualTo(dpWorld);
        assertThat(cfo.get("companyName").asText()).isEqualTo("DP World");
        assertThat(cfo.get("linkedinUrl").asText()).isEqualTo("https://www.linkedin.com/in/risalat-rehman/");
        assertThat(cfo.get("title").asText()).isEqualTo("Chief Financial Officer at DP World Jeddah");
        assertThat(cfo.get("enrichedAt").isNull()).isFalse();
        assertThat(cfo.get("nationality").asText()).isEqualTo("Emirati");
        assertThat(db.queryForObject("select enriched_by from app_lm_project_candidate where id = ?::uuid",
                String.class, cfo.get("id").asText())).isEqualTo("BRIGHTDATA");

        assertThat(db.queryForObject("select count(*) from app_lm_audit_event where target_id = ? and event_type in "
                + "('EXECUTIVE_SOURCING_REQUESTED', 'EXECUTIVE_SOURCING_COMPLETED')", Integer.class, projectId))
                .isEqualTo(2);

        mvc.perform(get(sourcingUrl(projectId) + "/latest").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(runId));
    }

    @Test
    @DisplayName("someone the mandate already maps is left out of the rerank, and a pick that collides is a skip")
    void alreadyMappedPeopleAreSkipped() throws Exception {
        String projectId = mandate("Held Firm");
        String dpWorld = company(projectId, "DP World", "https://www.linkedin.com/company/dp-world/");
        mvc.perform(post(candidatesUrl(projectId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Already Held","triageCompanyId":"%s",
                                 "linkedinUrl":"https://www.linkedin.com/in/Already-Held/"}""".formatted(dpWorld)))
                .andExpect(status().isCreated());
        mvc.perform(post(candidatesUrl(projectId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Group Director","triageCompanyId":"%s"}""".formatted(dpWorld)))
                .andExpect(status().isCreated());
        peopleSearch.answerWith("dp-world", List.of(
                person("already-held", "Already Held", "Chief Financial Officer"),
                person("risalat-rehman", "Risalat Rehman", "Chief Financial Officer at DP World Jeddah"),
                person("group-director", "Group Director", "Group Director - Financial Accounts")));

        String runId = start(projectId, "[\"" + dpWorld + "\"]");

        JsonNode outcome = outcomeFor(runOf(projectId, runId).get("outcomes"), dpWorld);
        assertThat(outcome.get("outcome").asText()).isEqualTo("FILED");
        assertThat(model.prompts().getFirst().getUserMessage().getText()).doesNotContain("Already Held");
        assertThat(outcome.get("filed").asInt()).isEqualTo(1);
        assertThat(outcome.get("skipped").asInt()).isEqualTo(1);
        assertThat(outcome.get("picks").get(1).get("candidateId").isNull()).isTrue();
        assertThat(candidatesOf(projectId)).hasSize(3);
    }

    @Test
    @DisplayName("more companies than the cap, a company from another stage, and a second run are refused unspent")
    void overTheCapAStrayCompanyAndASecondRunAreRefused() throws Exception {
        String projectId = mandate("Refusal Firm");
        String[] ids = new String[7];
        for (int index = 0; index < ids.length; index++) {
            ids[index] = company(projectId, "Company " + index, "https://www.linkedin.com/company/co-" + index);
        }
        mvc.perform(patch(triageUrl(projectId) + "/" + ids[6])
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"shortlisted\"}"))
                .andExpect(status().isOk());

        mvc.perform(post(sourcingUrl(projectId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"triageCompanyIds\":[\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"]}"
                                .formatted(ids[0], ids[1], ids[2], ids[3], ids[4], ids[5])))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EXECUTIVE_SOURCING_TOO_MANY_COMPANIES"));
        mvc.perform(post(sourcingUrl(projectId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"triageCompanyIds\":[\"%s\"]}".formatted(ids[6])))
                .andExpect(status().isBadRequest());
        assertThat(peopleSearch.searches()).isEmpty();

        String runId = start(projectId, "[]");
        assertThat(runOf(projectId, runId).get("companiesTotal").asInt()).isEqualTo(5);
        assertThat(peopleSearch.searches()).hasSize(5);
    }

    @Test
    @DisplayName("with nothing ticked, a company that already has an executive mapped is not taken")
    void anUntickedRunSkipsCompaniesWithAnExecutive() throws Exception {
        String projectId = mandate("Skip Firm");
        String mapped = company(projectId, "Alpha Mapped", "https://www.linkedin.com/company/alpha-mapped/");
        String open = company(projectId, "Beta Open", "https://www.linkedin.com/company/beta-open/");
        mvc.perform(post(candidatesUrl(projectId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Already Here","triageCompanyId":"%s"}""".formatted(mapped)))
                .andExpect(status().isCreated());

        String runId = start(projectId, "[]");

        JsonNode run = runOf(projectId, runId);
        assertThat(run.get("companiesTotal").asInt()).isEqualTo(1);
        assertThat(run.get("outcomes").get(0).get("triageCompanyId").asText()).isEqualTo(open);
    }

    @Test
    @DisplayName("a client representative can neither start nor read a run, and an unoffered deployment refuses")
    void clientSeatsAndUnofferedDeploymentsAreRefused() throws Exception {
        String projectId = mandate("Access Firm");
        company(projectId, "DP World", "https://www.linkedin.com/company/dp-world/");
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

        mvc.perform(post(sourcingUrl(projectId))
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"triageCompanyIds\":[]}"))
                .andExpect(status().isForbidden());
        mvc.perform(get(sourcingUrl(projectId) + "/latest").header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isForbidden());

        peopleSearch.offer(false);
        mvc.perform(get("/api/v1/executive-sourcing/config").header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.maxCompaniesPerRun").value(5));
        mvc.perform(post(sourcingUrl(projectId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"triageCompanyIds\":[]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXECUTIVE_SOURCING_UNAVAILABLE"));
    }

    @Test
    @DisplayName("a second mandate on the same company is answered from the people cache — nobody bought twice")
    void nobodyIsBoughtTwice() throws Exception {
        String firstProject = mandate("First Cache Firm");
        company(firstProject, "DP World", "https://www.linkedin.com/company/dp-world/");
        peopleSearch.answerWith("dp-world", List.of(
                person("risalat-rehman", "Risalat Rehman", "Chief Financial Officer at DP World Jeddah"),
                person("group-director", "Group Director", "Head of Group Finance")));
        String firstRun = start(firstProject, "[]");
        assertThat(runOf(firstProject, firstRun).get("vendorHits").asInt()).isEqualTo(2);
        assertThat(peopleSearch.searches()).hasSize(1);

        String secondProject = mandate("Second Cache Firm");
        String secondCompany = company(secondProject, "DP World", "https://www.linkedin.com/company/dp-world/");
        String secondRun = start(secondProject, "[]");

        JsonNode run = runOf(secondProject, secondRun);
        assertThat(peopleSearch.searches()).hasSize(1);
        assertThat(run.get("vendorHits").asInt()).isZero();
        assertThat(run.get("cachedHits").asInt()).isEqualTo(2);
        assertThat(outcomeFor(run.get("outcomes"), secondCompany).get("filed").asInt()).isEqualTo(2);
        assertThat(candidatesOf(secondProject)).hasSize(2);
    }

    @Test
    @DisplayName("a different search at a company already on file excludes everyone on file and buys only the new")
    void aNewQuestionBuysOnlyNewPeople() throws Exception {
        String firstProject = mandate("Words One Firm");
        company(firstProject, "DP World", "https://www.linkedin.com/company/dp-world/");
        peopleSearch.answerWith("dp-world", List.of(
                person("risalat-rehman", "Risalat Rehman", "Chief Financial Officer at DP World Jeddah")));
        start(firstProject, "[]");

        peopleSearch.answerWith("dp-world", List.of(
                person("risalat-rehman", "Risalat Rehman", "Chief Financial Officer at DP World Jeddah"),
                person("new-person", "New Person", "Head of Finance Operations")));
        db.update("DELETE FROM app_lm_vendor_people_search");
        String secondProject = mandate("Words Two Firm");
        company(secondProject, "DP World", "https://www.linkedin.com/company/dp-world/");
        String secondRun = start(secondProject, "[]");

        RecordingPeopleSearch.Asked second = peopleSearch.searches().getLast();
        assertThat(second.excludedSlugs()).containsExactly("risalat-rehman");
        JsonNode run = runOf(secondProject, secondRun);
        assertThat(run.get("vendorHits").asInt()).isEqualTo(1);
        assertThat(run.get("cachedHits").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("the search keeps to the position's country and its Gulf neighbours, and never leaves them")
    void theSearchKeepsToThePositionsRegion() throws Exception {
        String projectId = mandate("Regional Firm");
        String dpWorld = company(projectId, "DP World", "https://www.linkedin.com/company/dp-world/");
        db.update("UPDATE app_lm_position SET location_country = 'United Arab Emirates' WHERE project_id = ?::uuid",
                projectId);

        String runId = start(projectId, "[]");

        assertThat(peopleSearch.searches()).hasSize(1);
        assertThat(peopleSearch.searches().getFirst().countryCodes())
                .containsExactly("AE", "SA", "QA", "KW", "BH", "OM");
        assertThat(outcomeFor(runOf(projectId, runId).get("outcomes"), dpWorld).get("outcome").asText())
                .isEqualTo("NO_HITS");
    }

    @Test
    @DisplayName("a vendor failure at one company is that company's outcome; the run still completes")
    void aVendorFailureIsOneCompanysOutcome() throws Exception {
        String projectId = mandate("Failing Vendor Firm");
        String dpWorld = company(projectId, "DP World", "https://www.linkedin.com/company/dp-world/");
        peopleSearch.failWith(new IllegalStateException("vendor down"));

        String runId = start(projectId, "[]");

        JsonNode run = runOf(projectId, runId);
        assertThat(run.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(outcomeFor(run.get("outcomes"), dpWorld).get("outcome").asText()).isEqualTo("FAILED");
        assertThat(candidatesOf(projectId)).isEmpty();
    }

    private String start(String projectId, String idsJson) throws Exception {
        return body(mvc.perform(post(sourcingUrl(projectId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"triageCompanyIds\":" + idsJson + "}"))
                .andExpect(status().isAccepted())
                .andReturn()).get("id").asText();
    }

    private JsonNode runOf(String projectId, String runId) throws Exception {
        return body(mvc.perform(get(sourcingUrl(projectId) + "/" + runId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn());
    }

    private static JsonNode outcomeFor(JsonNode outcomes, String triageCompanyId) {
        for (JsonNode outcome : outcomes) {
            if (outcome.get("triageCompanyId").asText().equals(triageCompanyId)) {
                return outcome;
            }
        }
        throw new AssertionError("No outcome for " + triageCompanyId + " in " + outcomes);
    }

    private JsonNode candidatesOf(String projectId) throws Exception {
        return body(mvc.perform(get(candidatesUrl(projectId))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn()).get("candidates");
    }

    private String company(String projectId, String name, String linkedinUrl) throws Exception {
        String page = linkedinUrl == null ? "" : ",\"companyLinkedinUrl\":\"" + linkedinUrl + "\"";
        return body(mvc.perform(post(triageUrl(projectId) + "/capture")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"" + name + "\"" + page + "}"))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private static BrightDataPerson person(String slug, String name, String position) {
        return new BrightDataPerson(slug, slug, name, "https://www.linkedin.com/in/" + slug, "About " + name,
                position, "Dubai", "Dubai, United Arab Emirates", "AE", "DP World",
                new BrightDataPerson.BrightDataCurrentCompany("DP World", "dp-world", null), null, true,
                List.of(new BrightDataExperience("DP World", position, null, null, null, "2020", null, null, null)),
                List.of(), List.of(), List.of());
    }

    private static String sourcingUrl(String projectId) {
        return "/api/v1/projects/" + projectId + "/executive-sourcing";
    }

    private static String candidatesUrl(String projectId) {
        return "/api/v1/projects/" + projectId + "/candidates";
    }

    private static String triageUrl(String projectId) {
        return "/api/v1/projects/" + projectId + "/triage";
    }

    /** Its own admin per firm, so one test can open two mandates in two workspaces. */
    private String mandate(String firmName) throws Exception {
        String alok = firmName.toLowerCase().replaceAll("[^a-z]", "") + "@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), firmName);
        adminToken = login(alok);
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
