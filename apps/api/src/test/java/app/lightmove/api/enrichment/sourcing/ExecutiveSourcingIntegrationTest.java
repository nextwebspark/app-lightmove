package app.lightmove.api.enrichment.sourcing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.StubChatModel;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson.BrightDataExperience;
import app.lightmove.api.enrichment.sourcing.service.RecordingLiveProfiles;
import app.lightmove.api.enrichment.sourcing.service.RecordingProfileIndex;
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
 * {@code SynchronousAuditWrites}) against the two recording indexes and the stub model, and the people
 * found who hold the seat land as AI-sourced executives at their companies.
 *
 * <p>The stub answers every prompt of a run with one document, so it carries every shape the run asks
 * for: the search words, and the automatic AI run's background and nationality. Each call binds the half
 * it asked for.
 */
@IntegrationTest
class ExecutiveSourcingIntegrationTest extends FlowTestSupport {

    private static final String EVERY_ANSWER = """
            {"levels":["C_LEVEL","SENIOR_VP","VP_HEAD_DIRECTOR"],"functionTerms":["Finance","Financial"],
             "localLevelWords":[],"standaloneTitles":["CFO"],"excludedWords":["Assistant"],
             "suggestions":["Treasury"],"roleSummary":"The group's finance chief.",
             "category":"Emirati","confidence":"high","evidence_for":[],"evidence_against":[],"rule_applied":"none",
             "gender":"male","yearsExperience":20,"seniority":"C-Suite"}""";

    private static final String WORDS_PROMPT = "You are a researcher on an executive search mandate";

    @Autowired private RecordingLiveProfiles live;
    @Autowired private RecordingProfileIndex index;
    @Autowired private StubChatModel model;
    @Autowired private JdbcTemplate db;

    private String adminToken;

    @BeforeEach
    void resetTheIndexes() {
        live.clear();
        index.clear();
        model.answerWith(EVERY_ANSWER);
    }

    @AfterEach
    void resetTheModel() {
        model.reset();
    }

    @Test
    @DisplayName("a run buys the seat's people up to the company's budget, files the best fits, and enriches them")
    void aRunFilesThePicks() throws Exception {
        String projectId = mandate("Sourcing Firm");
        String dpWorld = company(projectId, "DP World", "https://www.linkedin.com/company/dp-world/");
        String noPage = company(projectId, "Hand Typed Co", null);
        live.answerWith("dp-world", List.of(
                person("risalat-rehman", "Risalat Rehman", "Chief Financial Officer at DP World Jeddah"),
                person("group-director", "Group Director", "Group Director - Financial Accounts"),
                person("third-person", "Third Person", "Head of Finance Transformation"),
                person("fourth-person", "Fourth Person", "Head of Finance Operations")));

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
        assertThat(run.get("executivesFiled").asInt()).isEqualTo(3);
        assertThat(run.get("vendorHits").asInt()).as("three profiles bought, the fourth left unbought").isEqualTo(3);
        JsonNode outcomes = run.get("outcomes");
        assertThat(outcomes).hasSize(2);
        JsonNode filed = outcomeFor(outcomes, dpWorld);
        assertThat(filed.get("outcome").asText()).isEqualTo("FILED");
        assertThat(filed.get("filed").asInt()).isEqualTo(3);
        assertThat(filed.get("picks")).extracting(pick -> pick.get("name").asText())
                .containsExactly("Risalat Rehman", "Group Director", "Third Person");
        assertThat(filed.get("picks").get(0).get("score").isNull()).isTrue();
        assertThat(filed.get("seen").asInt()).isEqualTo(3);
        assertThat(filed.get("matched").asLong()).isEqualTo(4);
        assertThat(outcomeFor(outcomes, noPage).get("outcome").asText()).isEqualTo("NO_LINKEDIN_PAGE");
        assertThat(run.get("searchedFor").get("functionWords")).extracting(JsonNode::asText)
                .containsExactly("Finance", "Financial", "CFO");
        assertThat(run.get("searchedFor").get("seniorityWords")).extracting(JsonNode::asText)
                .contains("Chief", "SVP", "Head", "Director");
        assertThat(run.get("searchedFor").get("excludedWords")).extracting(JsonNode::asText)
                .containsExactly("Assistant");

        assertThat(live.searches()).singleElement().satisfies(search -> {
            assertThat(search.companySlug()).isEqualTo("dp-world");
            assertThat(search.size()).isEqualTo(3);
            assertThat(search.countryCodes()).isEmpty();
        });
        assertThat(index.searches()).as("the company with no page is searched nowhere").hasSize(1);

        JsonNode people = candidatesOf(projectId);
        assertThat(people).hasSize(3);
        JsonNode cfo = null;
        for (JsonNode person : people) {
            if (person.get("fullName").asText().equals("Risalat Rehman")) {
                cfo = person;
            }
        }
        assertThat(cfo).isNotNull();
        assertThat(cfo.get("source").asText()).isEqualTo("ai_sourced");
        assertThat(cfo.get("triageCompanyId").asText()).isEqualTo(dpWorld);
        assertThat(cfo.get("companyName").asText()).isEqualTo("DP World");
        assertThat(cfo.get("linkedinUrl").asText()).isEqualTo("https://www.linkedin.com/in/risalat-rehman/");
        assertThat(cfo.get("title").asText()).isEqualTo("Chief Financial Officer at DP World Jeddah");
        assertThat(cfo.get("enrichedAt").isNull()).isFalse();
        assertThat(cfo.get("nationality").asText()).isEqualTo("Emirati");
        assertThat(enrichedByOf(cfo)).isEqualTo("CONTACTOUT");

        assertThat(db.queryForObject("select count(*) from app_lm_audit_event where target_id = ? and event_type in "
                + "('EXECUTIVE_SOURCING_REQUESTED', 'EXECUTIVE_SOURCING_COMPLETED')", Integer.class, projectId))
                .isEqualTo(2);
        assertThat(db.queryForList("select units from app_lm_usage_event where project_id = ?::uuid and kind = ?",
                Integer.class, projectId, "SOURCING_RUN")).as("the executives filed, once per run").containsExactly(3);

        mvc.perform(get(sourcingUrl(projectId) + "/latest").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(runId));
    }

    @Test
    @DisplayName("the picks are the people whose current role fits the seat best, not the first the index returned")
    void thePicksAreTheBestFitsNotTheFirstReturned() throws Exception {
        String projectId = mandate("Ranking Firm");
        String dpWorld = company(projectId, "DP World", "https://www.linkedin.com/company/dp-world/");
        db.update("UPDATE app_lm_position SET seniority = 'C_SUITE' WHERE project_id = ?::uuid", projectId);
        live.answerWith("dp-world", List.of(
                person("finance-manager", "Finance Manager", "Finance Manager"),
                person("head-of-finance", "Head of Finance", "Head of Finance"),
                person("finance-director", "Finance Director", "Group Finance Director"),
                person("the-cfo", "The CFO", "Chief Financial Officer")));

        String runId = start(projectId, "[]");

        JsonNode outcome = outcomeFor(runOf(projectId, runId).get("outcomes"), dpWorld);
        assertThat(outcome.get("picks")).extracting(pick -> pick.get("name").asText())
                .containsExactly("The CFO", "Head of Finance", "Finance Director");
    }

    @Test
    @DisplayName("an untitled person the live index confirms by name is filed from a profile bought then; one it cannot is not")
    void aConfirmedPersonIsFiledFromTheirLiveProfile() throws Exception {
        String projectId = mandate("Confirmed Firm");
        String dpWorld = company(projectId, "DP World", "https://www.linkedin.com/company/dp-world/");
        db.update("UPDATE app_lm_position SET location_country = 'United Arab Emirates' WHERE project_id = ?::uuid",
                projectId);
        live.answerWith("dp-world", List.of(
                personLivingIn("rania-haddad", "Rania Haddad", "Head of Finance", "GB")));
        index.answerWith("dp-world", List.of(
                untitled("rania-haddad", "Rania Haddad"),
                untitled("omar-khalil", "Omar Khalil")));

        String runId = start(projectId, "[]");

        JsonNode run = runOf(projectId, runId);
        JsonNode outcome = outcomeFor(run.get("outcomes"), dpWorld);
        assertThat(outcome.get("outcome").asText()).isEqualTo("FILED");
        assertThat(outcome.get("picks")).extracting(pick -> pick.get("name").asText())
                .containsExactly("Rania Haddad");
        assertThat(live.searches()).as("nobody holds the title where the role is").isEmpty();
        assertThat(live.titleChecks()).containsExactly("Rania Haddad", "Omar Khalil");
        assertThat(live.profilesBought()).containsExactly("rania-haddad");
        assertThat(run.get("vendorHits").asInt()).isEqualTo(1);
        JsonNode filed = candidatesOf(projectId).get(0);
        assertThat(filed.get("title").asText()).isEqualTo("Head of Finance");
        assertThat(enrichedByOf(filed)).isEqualTo("CONTACTOUT");
    }

    @Test
    @DisplayName("with no live index, a fitting title on the profile index is filed as it stands")
    void theProfileIndexAloneFilesFittingTitles() throws Exception {
        String projectId = mandate("Index Firm");
        String dpWorld = company(projectId, "DP World", "https://www.linkedin.com/company/dp-world/");
        live.offer(false);
        index.answerWith("dp-world", List.of(
                person("head-of-finance", "Head of Finance", "Head of Finance"),
                person("a-pharmacist", "A Pharmacist", "Doctor of Pharmacy"),
                untitled("no-title", "No Title")));

        String runId = start(projectId, "[]");

        JsonNode run = runOf(projectId, runId);
        JsonNode outcome = outcomeFor(run.get("outcomes"), dpWorld);
        assertThat(outcome.get("picks")).extracting(pick -> pick.get("name").asText())
                .containsExactly("Head of Finance");
        assertThat(outcome.get("notFit").asInt()).isEqualTo(1);
        assertThat(run.get("vendorHits").asInt()).isZero();
        assertThat(enrichedByOf(candidatesOf(projectId).get(0))).isEqualTo("BRIGHTDATA");
    }

    @Test
    @DisplayName("a run that would pass the month's fair-use ceiling is refused before anything is searched or queued")
    void aRunPastTheFairUseCeilingIsRefused() throws Exception {
        String projectId = mandate("Fair Use Firm");
        company(projectId, "DP World", "https://www.linkedin.com/company/dp-world/");
        db.update("""
                insert into app_lm_usage_event (workspace_id, project_id, kind, units, est_cost_fils)
                select workspace_id, id, 'SOURCING_RUN', 2000, 0 from app_lm_project where id = ?::uuid""", projectId);

        JsonNode refusal = body(mvc.perform(post(sourcingUrl(projectId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"triageCompanyIds\":[]}"))
                .andExpect(status().isTooManyRequests())
                .andReturn());

        assertThat(refusal.get("code").asText()).isEqualTo("FAIR_USE_REACHED");
        assertThat(refusal.get("kind").asText()).isEqualTo("SOURCING_RUN");
        assertThat(refusal.get("resetsAt").isNull()).isFalse();
        assertThat(index.searches()).isEmpty();
        assertThat(db.queryForObject("select count(*) from app_lm_executive_sourcing_run where project_id = ?::uuid",
                Integer.class, projectId)).isZero();
    }

    @Test
    @DisplayName("someone the mandate already maps is left out, and a hit that collides with a held name is a skip")
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
        live.answerWith("dp-world", List.of(
                person("already-held", "Already Held", "Chief Financial Officer"),
                person("risalat-rehman", "Risalat Rehman", "Chief Financial Officer at DP World Jeddah"),
                person("group-director", "Group Director", "Group Director - Financial Accounts")));

        String runId = start(projectId, "[\"" + dpWorld + "\"]");

        JsonNode outcome = outcomeFor(runOf(projectId, runId).get("outcomes"), dpWorld);
        assertThat(outcome.get("outcome").asText()).isEqualTo("FILED");
        assertThat(outcome.get("filed").asInt()).isEqualTo(1);
        assertThat(outcome.get("skipped").asInt()).isEqualTo(1);
        assertThat(outcome.get("picks").get(1).get("candidateId").isNull()).isTrue();
        assertThat(candidatesOf(projectId)).hasSize(3);
    }

    @Test
    @DisplayName("a company whose people found are all mapped already reads all already mapped")
    void onlyMappedPeopleIsAllAlreadyMapped() throws Exception {
        String projectId = mandate("Mapped Firm");
        String dpWorld = company(projectId, "DP World", "https://www.linkedin.com/company/dp-world/");
        mvc.perform(post(candidatesUrl(projectId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Already Held","triageCompanyId":"%s",
                                 "linkedinUrl":"https://www.linkedin.com/in/already-held/"}""".formatted(dpWorld)))
                .andExpect(status().isCreated());
        live.answerWith("dp-world", List.of(person("already-held", "Already Held", "Group Finance Director")));

        String runId = start(projectId, "[\"" + dpWorld + "\"]");

        JsonNode outcome = outcomeFor(runOf(projectId, runId).get("outcomes"), dpWorld);
        assertThat(outcome.get("outcome").asText()).isEqualTo("ALL_ALREADY_MAPPED");
        assertThat(candidatesOf(projectId)).hasSize(1);
    }

    @Test
    @DisplayName("more companies than the cap, a company from another stage, and a second run are refused unspent")
    void overTheCapAStrayCompanyAndASecondRunAreRefused() throws Exception {
        String projectId = mandate("Refusal Firm");
        String[] ids = new String[7];
        for (int number = 0; number < ids.length; number++) {
            ids[number] = company(projectId, "Company " + number, "https://www.linkedin.com/company/co-" + number);
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
        assertThat(index.searches()).isEmpty();

        String runId = start(projectId, "[]");
        assertThat(runOf(projectId, runId).get("companiesTotal").asInt()).isEqualTo(5);
        assertThat(index.searches()).hasSize(5);
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
    @DisplayName("a client representative can neither start nor read a run, and a deployment with neither index refuses")
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

        live.offer(false);
        mvc.perform(get("/api/v1/executive-sourcing/config").header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.enabled").value(true));
        index.offer(false);
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
        live.answerWith("dp-world", List.of(
                person("risalat-rehman", "Risalat Rehman", "Chief Financial Officer at DP World Jeddah"),
                person("group-director", "Group Director", "Head of Group Finance")));
        String firstRun = start(firstProject, "[]");
        assertThat(runOf(firstProject, firstRun).get("vendorHits").asInt()).isEqualTo(2);

        String secondProject = mandate("Second Cache Firm");
        String secondCompany = company(secondProject, "DP World", "https://www.linkedin.com/company/dp-world/");
        String secondRun = start(secondProject, "[]");

        JsonNode run = runOf(secondProject, secondRun);
        assertThat(live.searches()).hasSize(1);
        assertThat(index.searches()).hasSize(1);
        assertThat(run.get("vendorHits").asInt()).isZero();
        assertThat(run.get("cachedHits").asInt()).isEqualTo(2);
        assertThat(outcomeFor(run.get("outcomes"), secondCompany).get("filed").asInt()).isEqualTo(2);
        assertThat(candidatesOf(secondProject)).hasSize(2);
    }

    @Test
    @DisplayName("a company already on file is asked only for as many more people as the count says are new")
    void aCompanyOnFileIsAskedOnlyForTheRest() throws Exception {
        String firstProject = mandate("Words One Firm");
        company(firstProject, "DP World", "https://www.linkedin.com/company/dp-world/");
        live.answerWith("dp-world", List.of(
                person("risalat-rehman", "Risalat Rehman", "Chief Financial Officer at DP World Jeddah")));
        start(firstProject, "[]");

        live.answerWith("dp-world", List.of(
                person("new-person", "New Person", "Head of Finance Operations"),
                person("risalat-rehman", "Risalat Rehman", "Chief Financial Officer at DP World Jeddah")));
        db.update("DELETE FROM app_lm_vendor_people_search");
        String secondProject = mandate("Words Two Firm");
        company(secondProject, "DP World", "https://www.linkedin.com/company/dp-world/");
        String secondRun = start(secondProject, "[]");

        assertThat(live.searches().getLast().size()).isEqualTo(1);
        JsonNode run = runOf(secondProject, secondRun);
        assertThat(run.get("vendorHits").asInt()).isEqualTo(1);
        assertThat(run.get("cachedHits").asInt()).isEqualTo(1);
        assertThat(candidatesOf(secondProject)).hasSize(2);
    }

    @Test
    @DisplayName("the search keeps to the position's country, the Gulf and each company's own country")
    void theSearchKeepsToThePositionsRegion() throws Exception {
        String projectId = mandate("Regional Firm");
        String cairo = company(projectId, "Cairo Foods", "https://www.linkedin.com/company/cairo-foods/", "Egypt");
        db.update("UPDATE app_lm_position SET location_country = 'United Arab Emirates' WHERE project_id = ?::uuid",
                projectId);

        String runId = start(projectId, "[]");

        assertThat(index.searches()).singleElement().satisfies(search -> assertThat(search.countryCodes())
                .containsExactly("AE", "SA", "QA", "KW", "BH", "OM", "EG"));
        assertThat(wordsPrompts()).singleElement().asString().contains("Companies being searched are in: Egypt");
        assertThat(outcomeFor(runOf(projectId, runId).get("outcomes"), cairo).get("outcome").asText())
                .isEqualTo("NO_HITS");
        assertThat(db.queryForObject("select count(*) from app_lm_usage_event where project_id = ?::uuid",
                Integer.class, projectId)).as("a run that filed nobody used nothing").isZero();
    }

    @Test
    @DisplayName("a draft the model cannot give falls back to the role title's own words, and the run still searches")
    void anUndraftedRunSearchesTheRoleTitle() throws Exception {
        String projectId = mandate("Fallback Firm");
        String dpWorld = company(projectId, "DP World", "https://www.linkedin.com/company/dp-world/");
        model.answerWhenSystemContains(WORDS_PROMPT, "{}");
        live.answerWith("dp-world", List.of(person("the-cfo", "The CFO", "Group CFO")));

        String runId = start(projectId, "[]");

        JsonNode run = runOf(projectId, runId);
        assertThat(run.get("searchedFor").get("functionWords")).extracting(JsonNode::asText)
                .containsExactly("Finance", "Financial", "CFO");
        assertThat(run.get("searchedFor").get("excludedWords")).extracting(JsonNode::asText)
                .containsExactly("Assistant", "Secretary", "Coordinator", "Intern");
        assertThat(outcomeFor(run.get("outcomes"), dpWorld).get("filed").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("a company whose people found all fall outside the seat reads Nothing fit and files nobody")
    void onlyPeopleOutsideTheSeatIsNothingFit() throws Exception {
        String projectId = mandate("Nothing Fit Firm");
        String dpWorld = company(projectId, "DP World", "https://www.linkedin.com/company/dp-world/");
        index.answerWith("dp-world", List.of(
                person("a-pharmacist", "A Pharmacist", "Doctor of Pharmacy"),
                person("a-medical-director", "A Medical Director", "DHA pharmacist / Medical director")));

        String runId = start(projectId, "[]");

        JsonNode run = runOf(projectId, runId);
        JsonNode outcome = outcomeFor(run.get("outcomes"), dpWorld);
        assertThat(outcome.get("outcome").asText()).isEqualTo("NOTHING_FIT");
        assertThat(outcome.get("notFit").asInt()).isEqualTo(2);
        assertThat(outcome.get("picks")).isEmpty();
        assertThat(run.get("executivesFiled").asInt()).isZero();
        assertThat(candidatesOf(projectId)).isEmpty();
        assertThat(db.queryForObject("select count(*) from app_lm_usage_event where project_id = ?::uuid",
                Integer.class, projectId)).as("a run that filed nobody used nothing").isZero();
    }

    @Test
    @DisplayName("a vendor failure at one company is that company's outcome; the run still completes")
    void aVendorFailureIsOneCompanysOutcome() throws Exception {
        String projectId = mandate("Failing Vendor Firm");
        String dpWorld = company(projectId, "DP World", "https://www.linkedin.com/company/dp-world/");
        live.failWith(new IllegalStateException("vendor down"));

        String runId = start(projectId, "[]");

        JsonNode run = runOf(projectId, runId);
        assertThat(run.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(outcomeFor(run.get("outcomes"), dpWorld).get("outcome").asText()).isEqualTo("FAILED");
        assertThat(candidatesOf(projectId)).isEmpty();
    }

    private List<String> wordsPrompts() {
        return model.prompts().stream()
                .filter(prompt -> prompt.getSystemMessage().getText().contains(WORDS_PROMPT))
                .map(prompt -> prompt.getContents())
                .toList();
    }

    private String enrichedByOf(JsonNode candidate) {
        return db.queryForObject("select p.enriched_by from app_lm_person p join app_lm_project_candidate c "
                + "on c.person_id = p.id where c.id = ?::uuid", String.class, candidate.get("id").asText());
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
        return company(projectId, name, linkedinUrl, null);
    }

    private String company(String projectId, String name, String linkedinUrl, String country) throws Exception {
        String page = linkedinUrl == null ? "" : ",\"companyLinkedinUrl\":\"" + linkedinUrl + "\"";
        String where = country == null ? "" : ",\"companyCountry\":\"" + country + "\"";
        return body(mvc.perform(post(triageUrl(projectId) + "/capture")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"" + name + "\"" + page + where + "}"))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private static BrightDataPerson person(String slug, String name, String position) {
        return personLivingIn(slug, name, position, "AE");
    }

    private static BrightDataPerson personLivingIn(String slug, String name, String position, String countryCode) {
        return new BrightDataPerson(slug, slug, name, "https://www.linkedin.com/in/" + slug, "About " + name,
                position, "Dubai", "Dubai, United Arab Emirates", countryCode, "DP World",
                new BrightDataPerson.BrightDataCurrentCompany("DP World", "dp-world", null), null, true,
                List.of(new BrightDataExperience("DP World", position, null, null, null, "2020", null, null, null)),
                List.of(), List.of(), List.of());
    }

    /** As the profile index holds most people now: at the company, with no title on record. */
    private static BrightDataPerson untitled(String slug, String name) {
        return new BrightDataPerson(slug, slug, name, "https://www.linkedin.com/in/" + slug, null, null, "Dubai",
                "Dubai, United Arab Emirates", "AE", "DP World",
                new BrightDataPerson.BrightDataCurrentCompany("DP World", "dp-world", null), null, true, List.of(),
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
