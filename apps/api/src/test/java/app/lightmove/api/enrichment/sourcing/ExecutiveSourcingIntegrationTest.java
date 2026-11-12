package app.lightmove.api.enrichment.sourcing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FixtureJdbc;
import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.StubChatModel;
import app.lightmove.api.core.tenant.WorkspaceScope;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson.BrightDataExperience;
import app.lightmove.api.enrichment.sourcing.model.ExecutiveSourcingRequested;
import app.lightmove.api.enrichment.sourcing.model.ExecutiveSourcingRun;
import app.lightmove.api.enrichment.sourcing.model.SearchCriteria;
import app.lightmove.api.enrichment.sourcing.model.SearchWords;
import app.lightmove.api.enrichment.sourcing.model.SourcingCompany;
import app.lightmove.api.enrichment.sourcing.repository.ExecutiveSourcingRunRepository;
import app.lightmove.api.enrichment.sourcing.service.RecordingLiveProfiles;
import app.lightmove.api.enrichment.sourcing.service.RecordingProfileIndex;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

/**
 * Find executives end to end, as the panel drives it: the saved search, its free counts, a search that stores what
 * it found at each company and files nobody, the results read back, and Add. The worker runs inline (see
 * {@code SynchronousAuditWrites}) against the two recording indexes and the stub model.
 *
 * <p>The stub answers every prompt with one document, so it carries every shape asked for: the search words, and
 * the automatic AI run's background and nationality once someone is added.
 */
@IntegrationTest
@TestPropertySource(properties = "lightmove.billing.enforce=true")
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
    @Autowired @FixtureJdbc private JdbcTemplate db;
    @Autowired private ExecutiveSourcingRunRepository runs;
    @Autowired private ApplicationEventPublisher events;
    @Autowired private PlatformTransactionManager transactions;

    private String adminToken;
    private String adminEmail;

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
    @DisplayName("the search is drafted from the brief once, saved as the consultant edits it, and drafted again on Reset")
    void theSavedSearch() throws Exception {
        String projectId = mandate("Criteria Firm");
        inTheUae(projectId);
        mvc.perform(get(sourcingUrl(projectId) + "/criteria").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        JsonNode drafted = postJson(sourcingUrl(projectId) + "/criteria/suggest", "{\"triageCompanyIds\":[]}");
        assertThat(drafted.get("source").asText()).isEqualTo("AI");
        assertThat(drafted.at("/criteria/functionTerms")).extracting(JsonNode::asText)
                .containsExactly("Finance", "Financial");
        assertThat(drafted.at("/criteria/countryCodes")).extracting(JsonNode::asText)
                .containsExactly("AE", "SA", "QA", "KW", "BH", "OM");
        assertThat(drafted.at("/criteria/perCompany").asInt()).isEqualTo(3);

        JsonNode edited = body(mvc.perform(put(sourcingUrl(projectId) + "/criteria")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(criteriaJson("[\"Finance\",\"Treasury\"]", "[\"CFO\"]", 5)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(edited.get("source").asText()).isEqualTo("EDITED");
        assertThat(edited.at("/criteria/functionTerms")).extracting(JsonNode::asText)
                .containsExactly("Finance", "Treasury");

        JsonNode reset = postJson(sourcingUrl(projectId) + "/criteria/suggest", "{\"triageCompanyIds\":[]}");
        assertThat(reset.get("source").asText()).isEqualTo("AI");
        assertThat(reset.at("/criteria/perCompany").asInt()).as("the consultant's own choice stands").isEqualTo(5);
        assertThat(reset.get("briefChanged").asBoolean()).isFalse();

        db.update("UPDATE app_lm_position SET department = 'Group Finance' WHERE project_id = ?::uuid", projectId);
        mvc.perform(get(sourcingUrl(projectId) + "/criteria").header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.briefChanged").value(true));
    }

    @Test
    @DisplayName("counts are free: each company, each level and each suggested word, and nothing is bought")
    void countsAreFree() throws Exception {
        String projectId = mandate("Counts Firm");
        inTheUae(projectId);
        String dpWorld = company(projectId, "DP World", "https://www.linkedin.com/company/dp-world/");
        String noPage = company(projectId, "Hand Typed Co", null);
        live.answerWith("dp-world", List.of(
                person("the-cfo", "The CFO", "Chief Financial Officer"),
                person("head-finance", "Head of Finance", "Head of Finance"),
                person("treasurer", "A Treasurer", "Head of Treasury"),
                person("manager", "A Manager", "Finance Manager")));

        JsonNode counts = postJson(sourcingUrl(projectId) + "/counts",
                "{\"triageCompanyIds\":[],\"criteria\":" + criteriaJson("[\"Finance\",\"Financial\"]",
                        "[\"CFO\"]", 3).replace("\"suggestions\":[]", "\"suggestions\":[\"Treasury\"]") + "}");

        assertThat(counts.get("counted").asBoolean()).isTrue();
        JsonNode atDpWorld = companyIn(counts.get("companies"), dpWorld);
        assertThat(atDpWorld.get("match").asLong()).isEqualTo(2);
        assertThat(atDpWorld.get("toBuy").asLong()).isEqualTo(2);
        assertThat(companyIn(counts.get("companies"), noPage).get("searchable").asBoolean()).isFalse();
        assertThat(levelCount(counts, "C_LEVEL")).isEqualTo(1);
        assertThat(levelCount(counts, "VP_HEAD_DIRECTOR")).isEqualTo(1);
        assertThat(levelCount(counts, "MANAGER")).isEqualTo(1);
        assertThat(counts.at("/words/0/word").asText()).isEqualTo("Treasury");
        assertThat(counts.at("/words/0/count").asLong()).isEqualTo(1);
        assertThat(live.searches()).isEmpty();
        assertThat(index.searches()).isEmpty();
    }

    @Test
    @DisplayName("a search stores what it found at each company, judged and ranked, and files nobody")
    void aSearchStoresWhatItFound() throws Exception {
        String projectId = mandate("Search Firm");
        inTheUae(projectId);
        String dpWorld = company(projectId, "DP World", "https://www.linkedin.com/company/dp-world/");
        String noPage = company(projectId, "Hand Typed Co", null);
        live.answerWith("dp-world", List.of(
                person("risalat-rehman", "Risalat Rehman", "Chief Financial Officer at DP World Jeddah"),
                person("group-director", "Group Director", "Group Director - Financial Accounts")));
        index.answerWith("dp-world", List.of(
                person("a-pharmacist", "A Pharmacist", "Doctor of Pharmacy"),
                untitled("omar-khalil", "Omar Khalil")));

        String runId = start(projectId, "[]");

        JsonNode run = runOf(projectId, runId);
        assertThat(run.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(run.get("companiesTotal").asInt()).isEqualTo(2);
        assertThat(run.get("suggested").asInt()).isEqualTo(2);
        assertThat(run.get("profilesBought").asInt()).isEqualTo(2);
        assertThat(run.at("/searched/functionTerms")).extracting(JsonNode::asText)
                .containsExactly("Finance", "Financial");
        JsonNode result = outcomeFor(run.get("outcomes"), dpWorld);
        assertThat(result.get("outcome").asText()).isEqualTo("FOUND");
        assertThat(List.of(result.get("found").asInt(), result.get("fits").asInt(), result.get("noTitle").asInt(),
                result.get("notFit").asInt(), result.get("indexFound").asInt(), result.get("titleChecks").asInt()))
                .as("what the profile index alone returned, refuted by its title or a free check, is not listed")
                .containsExactly(2, 2, 0, 0, 2, 1);
        assertThat(outcomeFor(run.get("outcomes"), noPage).get("outcome").asText()).isEqualTo("NO_LINKEDIN_PAGE");
        JsonNode steps = progressFor(run.get("progress"), dpWorld);
        assertThat(steps.get("stage").asText()).as("the last step a searched company reaches").isEqualTo("RANKING");
        assertThat(steps.get("considered").asLong())
                .isEqualTo(result.get("matched").asLong() + result.get("indexFound").asLong());
        assertThat(steps.get("startedAt").isNull()).isFalse();
        assertThat(steps.get("finishedAt").isNull()).isFalse();
        assertThat(run.get("progress")).as("a company with no page is never started")
                .noneMatch(entry -> entry.get("triageCompanyId").asText().equals(noPage));

        JsonNode found = companyIn(suggestionsOf(projectId).get("companies"), dpWorld);
        assertThat(found.get("people")).extracting(person -> person.get("linkedinSlug").asText(),
                        person -> person.get("verdict").asText(),
                        person -> person.get("band").isNull() ? null : person.get("band").asText())
                .containsExactly(
                        tuple("risalat-rehman", "FITS", "STRONG"),
                        tuple("group-director", "FITS", "GOOD"));
        assertThat(found.at("/people/0/reasons")).extracting(JsonNode::asText)
                .contains("ROLE_WORD:Financial", "LEVEL:TOP", "LIVES_IN_ROLE_COUNTRY:AE");
        assertThat(found.at("/people/0/person/fullName").asText()).isEqualTo("Risalat Rehman");

        assertThat(candidatesOf(projectId)).isEmpty();
        assertThat(db.queryForObject("select count(*) from app_lm_audit_event where target_id = ? and event_type in "
                + "('EXECUTIVE_SOURCING_REQUESTED', 'EXECUTIVE_SOURCING_COMPLETED')", Integer.class, projectId))
                .isEqualTo(2);
        assertThat(db.queryForList("select units from app_lm_usage_event where project_id = ?::uuid and kind = ?",
                Integer.class, projectId, "SOURCING_RUN")).as("the profiles bought, once per run").containsExactly(2);
        mvc.perform(get(sourcingUrl(projectId) + "/latest").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(runId));
    }

    @Test
    @DisplayName("Add files the ticked people from what the search read, once; nobody a search did not find is added")
    void addFilesTheTicked() throws Exception {
        String projectId = mandate("Add Firm");
        String dpWorld = company(projectId, "DP World", "https://www.linkedin.com/company/dp-world/");
        live.answerWith("dp-world", List.of(
                person("risalat-rehman", "Risalat Rehman", "Chief Financial Officer at DP World Jeddah"),
                person("group-director", "Group Director", "Group Director - Financial Accounts")));
        start(projectId, "[]");
        int searchesBefore = live.searches().size();

        JsonNode added = add(projectId, dpWorld, "risalat-rehman", "group-director", "a-stranger");

        assertThat(added.get("added").asInt()).isEqualTo(2);
        assertThat(added.get("notFound").asInt()).isEqualTo(1);
        assertThat(live.searches()).as("no second vendor call").hasSize(searchesBefore);
        JsonNode people = candidatesOf(projectId);
        assertThat(people).hasSize(2);
        JsonNode cfo = people.get(0).get("fullName").asText().equals("Risalat Rehman") ? people.get(0) : people.get(1);
        assertThat(cfo.get("source").asText()).isEqualTo("ai_sourced");
        assertThat(cfo.get("triageCompanyId").asText()).isEqualTo(dpWorld);
        assertThat(cfo.get("title").asText()).isEqualTo("Chief Financial Officer at DP World Jeddah");
        assertThat(cfo.get("nationality").asText()).isEqualTo("Emirati");
        assertThat(enrichedByOf(cfo)).isEqualTo("CONTACTOUT");

        JsonNode again = add(projectId, dpWorld, "risalat-rehman");
        assertThat(again.get("added").asInt()).isZero();
        assertThat(again.get("alreadyOnPosition").asInt()).isEqualTo(1);
        assertThat(companyIn(suggestionsOf(projectId).get("companies"), dpWorld).at("/people/0/candidateId").asText())
                .isEqualTo(cfo.get("id").asText());
        assertThat(db.queryForObject("select count(*) from app_lm_audit_event where target_id = ? and event_type = "
                + "'EXECUTIVE_SOURCING_ADDED'", Integer.class, projectId)).isEqualTo(1);
    }

    @Test
    @DisplayName("an untitled person the live index confirms by name is shown from a profile bought within the budget")
    void aConfirmedPersonIsReadFromTheirLiveProfile() throws Exception {
        String projectId = mandate("Confirmed Firm");
        String dpWorld = company(projectId, "DP World", "https://www.linkedin.com/company/dp-world/");
        inTheUae(projectId);
        live.answerWith("dp-world", List.of(personLivingIn("rania-haddad", "Rania Haddad", "Head of Finance", "GB")));
        index.answerWith("dp-world", List.of(untitled("rania-haddad", "Rania Haddad"),
                untitled("omar-khalil", "Omar Khalil")));

        String runId = start(projectId, "[]");

        assertThat(live.searches()).as("nobody holds the title where the role is").isEmpty();
        assertThat(live.titleChecks()).containsExactly("Rania Haddad", "Omar Khalil");
        assertThat(live.profilesBought()).containsExactly("rania-haddad");
        assertThat(runOf(projectId, runId).get("profilesBought").asInt()).isEqualTo(1);
        JsonNode rania = companyIn(suggestionsOf(projectId).get("companies"), dpWorld).at("/people/0");
        assertThat(rania.get("verdict").asText()).isEqualTo("FITS");
        assertThat(rania.get("foundBy").asText()).isEqualTo("BOTH");
        assertThat(rania.at("/person/title").asText()).isEqualTo("Head of Finance");
        assertThat(candidatesOf(projectId)).isEmpty();
    }

    @Test
    @DisplayName("a stop asked for while a run is queued or searching leaves the companies not yet searched stopped")
    void aStoppedRun() throws Exception {
        String projectId = mandate("Stop Firm");
        String dpWorld = company(projectId, "DP World", "https://www.linkedin.com/company/dp-world/");
        UUID workspaceId = db.queryForObject("select workspace_id from app_lm_project where id = ?::uuid", UUID.class,
                projectId);
        UUID userId = db.queryForObject("select id from app_lm_user where email = ?", UUID.class, adminEmail);
        UUID runId = WorkspaceScope.call(workspaceId, () -> new TransactionTemplate(transactions).execute(status -> {
            ExecutiveSourcingRun queued = runs.save(ExecutiveSourcingRun.requested(workspaceId,
                    UUID.fromString(projectId), userId, List.of(new SourcingCompany(UUID.fromString(dpWorld),
                            "DP World", "dp-world", null, null, null)),
                    new SearchCriteria(SearchWords.defaultFor("Group CFO", null), List.of(), 3)));
            return queued.getId();
        }));

        mvc.perform(post(sourcingUrl(projectId) + "/" + runId + "/stop")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stopRequested").value(true));
        WorkspaceScope.run(workspaceId, () -> new TransactionTemplate(transactions).executeWithoutResult(status ->
                events.publishEvent(new ExecutiveSourcingRequested(runId, UUID.fromString(projectId), workspaceId,
                        userId))));

        JsonNode run = runOf(projectId, runId.toString());
        assertThat(run.get("status").asText()).isEqualTo("STOPPED");
        assertThat(outcomeFor(run.get("outcomes"), dpWorld).get("outcome").asText()).isEqualTo("STOPPED");
        assertThat(live.searches()).isEmpty();
    }

    @Test
    @DisplayName("results last as long as the people cache keeps who they were; Add then reads them as expired")
    void resultsExpireWithTheCache() throws Exception {
        String projectId = mandate("Expiry Firm");
        String dpWorld = company(projectId, "DP World", "https://www.linkedin.com/company/dp-world/");
        live.answerWith("dp-world", List.of(person("the-cfo", "The CFO", "Chief Financial Officer")));
        start(projectId, "[]");
        db.update("DELETE FROM app_lm_vendor_person");

        JsonNode company = companyIn(suggestionsOf(projectId).get("companies"), dpWorld);
        assertThat(company.get("expired").asBoolean()).isTrue();
        assertThat(company.get("people")).isEmpty();
        assertThat(add(projectId, dpWorld, "the-cfo").get("expired").asInt()).isEqualTo(1);
        assertThat(candidatesOf(projectId)).isEmpty();
    }

    @Test
    @DisplayName("a search that would pass the month's fair use, counted in profiles it may buy, is refused before anything runs")
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
        assertThat(index.searches()).isEmpty();
        assertThat(db.queryForObject("select count(*) from app_lm_executive_sourcing_run where project_id = ?::uuid",
                Integer.class, projectId)).isZero();
    }

    @Test
    @DisplayName("someone suppressed is left out of what a search found, and anyone listed since is hidden and withheld")
    void suppressedPeopleAreLeftOut() throws Exception {
        String projectId = mandate("Suppressed Hit Firm");
        String dpWorld = company(projectId, "DP World", "https://www.linkedin.com/company/dp-world/");
        suppress("linkedin.com/in/Asked-To-Leave");
        live.answerWith("dp-world", List.of(
                person("asked-to-leave", "Asked To Leave", "Chief Financial Officer"),
                person("risalat-rehman", "Risalat Rehman", "Chief Financial Officer at DP World Jeddah")));

        start(projectId, "[]");

        assertThat(companyIn(suggestionsOf(projectId).get("companies"), dpWorld).get("people"))
                .extracting(person -> person.get("linkedinSlug").asText())
                .containsExactly("risalat-rehman");
        suppress("linkedin.com/in/risalat-rehman");
        assertThat(companyIn(suggestionsOf(projectId).get("companies"), dpWorld).get("people"))
                .as("someone listed since the search is no longer shown").isEmpty();
        JsonNode added = add(projectId, dpWorld, "risalat-rehman", "asked-to-leave");
        assertThat(added.get("added").asInt()).isZero();
        assertThat(added.get("withheld").asInt()).isEqualTo(1);
        assertThat(added.get("notFound").asInt()).isEqualTo(1);
        assertThat(candidatesOf(projectId)).isEmpty();
    }

    @Test
    @DisplayName("someone whose processing is restricted is withheld from Add, and the rest are filed")
    void restrictedPeopleAreWithheld() throws Exception {
        String projectId = mandate("Restricted Run Firm");
        String elsewhere = anotherPosition();
        String personId = body(mvc.perform(post(candidatesUrl(elsewhere))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Asked To Wait","linkedinUrl":"https://www.linkedin.com/in/asked-to-wait/"}"""))
                .andExpect(status().isCreated()).andReturn()).get("personId").asText();
        mvc.perform(put("/api/v1/candidates/" + personId + "/processing-restriction")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"restricted":true,"reason":"Contests what we hold."}"""))
                .andExpect(status().isOk());
        String dpWorld = company(projectId, "DP World", "https://www.linkedin.com/company/dp-world/");
        live.answerWith("dp-world", List.of(
                person("asked-to-wait", "Asked To Wait", "Chief Financial Officer"),
                person("risalat-rehman", "Risalat Rehman", "Chief Financial Officer at DP World Jeddah")));
        start(projectId, "[]");

        JsonNode added = add(projectId, dpWorld, "asked-to-wait", "risalat-rehman");

        assertThat(added.get("added").asInt()).isEqualTo(1);
        assertThat(added.get("withheld").asInt()).isEqualTo(1);
        assertThat(candidatesOf(projectId)).extracting(candidate -> candidate.get("fullName").asText())
                .containsExactly("Risalat Rehman");
        assertThat(db.queryForObject("select count(*) from app_lm_project_candidate where person_id = ?::uuid",
                Long.class, personId)).isEqualTo(1);
    }

    @Test
    @DisplayName("more companies than the cap, a company from another stage, and a second run are refused unspent")
    void overTheCapAStrayCompanyAndASecondRunAreRefused() throws Exception {
        String projectId = mandate("Refusal Firm");
        String[] ids = new String[12];
        for (int number = 0; number < ids.length; number++) {
            ids[number] = company(projectId, "Company " + number, "https://www.linkedin.com/company/co-" + number);
        }
        mvc.perform(patch(triageUrl(projectId) + "/" + ids[11])
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"shortlisted\"}"))
                .andExpect(status().isOk());

        mvc.perform(post(sourcingUrl(projectId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"triageCompanyIds\":" + idsJson(Arrays.copyOf(ids, 11)) + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EXECUTIVE_SOURCING_TOO_MANY_COMPANIES"));
        mvc.perform(post(sourcingUrl(projectId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"triageCompanyIds\":[\"%s\"]}".formatted(ids[11])))
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
    @DisplayName("a client representative reaches none of it, and a deployment with neither index refuses a search")
    void clientSeatsAndUnofferedDeploymentsAreRefused() throws Exception {
        String projectId = mandate("Access Firm");
        String dpWorld = company(projectId, "DP World", "https://www.linkedin.com/company/dp-world/");
        String clientEmail = "client@client-" + domain;
        mvc.perform(post("/api/v1/projects/" + projectId + "/representatives/invitations")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"A Client","position":"Chair","email":"%s"}
                                """.formatted(clientEmail)))
                .andExpect(status().isOk());
        String bearer = "Bearer " + body(mvc.perform(post("/api/v1/onboarding/accept-invitation-signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","fullName":"A Client","password":"%s"}
                                """.formatted(email.latestTokenFor(clientEmail), PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn()).get("accessToken").asText();

        mvc.perform(post(sourcingUrl(projectId)).header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"triageCompanyIds\":[]}"))
                .andExpect(status().isForbidden());
        mvc.perform(get(sourcingUrl(projectId) + "/criteria").header("Authorization", bearer))
                .andExpect(status().isForbidden());
        mvc.perform(get(sourcingUrl(projectId) + "/suggestions").header("Authorization", bearer))
                .andExpect(status().isForbidden());
        mvc.perform(post(sourcingUrl(projectId) + "/suggestions/add").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"people\":[{\"triageCompanyId\":\"%s\",\"linkedinSlug\":\"x\"}]}"
                                .formatted(dpWorld)))
                .andExpect(status().isForbidden());

        live.offer(false);
        index.offer(false);
        mvc.perform(get("/api/v1/executive-sourcing/config").header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.maxCompaniesPerRun").value(5))
                .andExpect(jsonPath("$.perCompanyChoices[2]").value(10));
        mvc.perform(post(sourcingUrl(projectId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"triageCompanyIds\":[]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXECUTIVE_SOURCING_UNAVAILABLE"));
    }

    @Test
    @DisplayName("a second mandate searching the same company is answered from the people cache — nobody bought twice")
    void nobodyIsBoughtTwice() throws Exception {
        String firstProject = mandate("First Cache Firm");
        company(firstProject, "DP World", "https://www.linkedin.com/company/dp-world/");
        live.answerWith("dp-world", List.of(
                person("risalat-rehman", "Risalat Rehman", "Chief Financial Officer at DP World Jeddah"),
                person("group-director", "Group Director", "Head of Group Finance")));
        String firstRun = start(firstProject, "[]");
        assertThat(runOf(firstProject, firstRun).get("profilesBought").asInt()).isEqualTo(2);

        String secondProject = mandate("Second Cache Firm");
        String secondCompany = company(secondProject, "DP World", "https://www.linkedin.com/company/dp-world/");
        String secondRun = start(secondProject, "[]");

        JsonNode run = runOf(secondProject, secondRun);
        assertThat(live.searches()).hasSize(1);
        assertThat(index.searches()).hasSize(1);
        assertThat(run.get("profilesBought").asInt()).isZero();
        assertThat(run.get("profilesCached").asInt()).isEqualTo(2);
        assertThat(outcomeFor(run.get("outcomes"), secondCompany).get("fits").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("the search keeps to the search's countries and each company's own, and the draft is told the latter")
    void theSearchKeepsToItsCountries() throws Exception {
        String projectId = mandate("Regional Firm");
        inTheUae(projectId);
        String cairo = company(projectId, "Cairo Foods", "https://www.linkedin.com/company/cairo-foods/", "Egypt");
        postJson(sourcingUrl(projectId) + "/criteria/suggest", "{\"triageCompanyIds\":[\"" + cairo + "\"]}");

        String runId = start(projectId, "[]");

        assertThat(index.searches()).singleElement().satisfies(search -> assertThat(search.countryCodes())
                .containsExactly("AE", "SA", "QA", "KW", "BH", "OM", "EG"));
        assertThat(wordsPrompts()).singleElement().asString().contains("Companies being searched are in: Egypt");
        assertThat(outcomeFor(runOf(projectId, runId).get("outcomes"), cairo).get("outcome").asText())
                .isEqualTo("NO_HITS");
        assertThat(db.queryForObject("select count(*) from app_lm_usage_event where project_id = ?::uuid",
                Integer.class, projectId)).as("a search that bought nothing used nothing").isZero();
    }

    @Test
    @DisplayName("with nothing saved, a search runs on the role title's own words, and no model is asked")
    void anUnsavedSearchUsesTheRoleTitle() throws Exception {
        String projectId = mandate("Fallback Firm");
        String dpWorld = company(projectId, "DP World", "https://www.linkedin.com/company/dp-world/");
        live.answerWith("dp-world", List.of(person("the-cfo", "The CFO", "Group CFO")));

        String runId = start(projectId, "[]");

        JsonNode run = runOf(projectId, runId);
        assertThat(run.at("/searched/excludedWords")).extracting(JsonNode::asText)
                .containsExactly("Assistant", "Secretary", "Coordinator", "Intern");
        assertThat(outcomeFor(run.get("outcomes"), dpWorld).get("fits").asInt()).isEqualTo(1);
        assertThat(wordsPrompts()).isEmpty();
    }

    @Test
    @DisplayName("a vendor failure at one company is that company's result; the run still completes")
    void aVendorFailureIsOneCompanysOutcome() throws Exception {
        String projectId = mandate("Failing Vendor Firm");
        String dpWorld = company(projectId, "DP World", "https://www.linkedin.com/company/dp-world/");
        live.failWith(new IllegalStateException("vendor down"));

        String runId = start(projectId, "[]");

        JsonNode run = runOf(projectId, runId);
        assertThat(run.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(outcomeFor(run.get("outcomes"), dpWorld).get("outcome").asText()).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("a company that fails after buying profiles still meters what it bought")
    void aFailureAfterBuyingStillMetersTheProfiles() throws Exception {
        String projectId = mandate("Half Spent Firm");
        String dpWorld = company(projectId, "DP World", "https://www.linkedin.com/company/dp-world/");
        live.answerWith("dp-world", List.of(
                person("risalat-rehman", "Risalat Rehman", "Chief Financial Officer at DP World Jeddah"),
                person("group-director", "Group Director", "Group Director - Financial Accounts")));
        index.failWith(new IllegalStateException("index down"));

        String runId = start(projectId, "[]");

        JsonNode run = runOf(projectId, runId);
        JsonNode result = outcomeFor(run.get("outcomes"), dpWorld);
        assertThat(result.get("outcome").asText()).isEqualTo("FAILED");
        assertThat(result.get("bought").asInt()).isEqualTo(2);
        assertThat(run.get("profilesBought").asInt()).isEqualTo(2);
        assertThat(db.queryForList("select units from app_lm_usage_event where project_id = ?::uuid and kind = ?",
                Integer.class, projectId, "SOURCING_RUN")).containsExactly(2);
    }

    private JsonNode postJson(String url, String json) throws Exception {
        return body(mvc.perform(post(url)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andReturn());
    }

    private JsonNode add(String projectId, String triageCompanyId, String... slugs) throws Exception {
        String people = Arrays.stream(slugs)
                .map(slug -> "{\"triageCompanyId\":\"%s\",\"linkedinSlug\":\"%s\"}".formatted(triageCompanyId, slug))
                .collect(Collectors.joining(","));
        return postJson(sourcingUrl(projectId) + "/suggestions/add", "{\"people\":[" + people + "]}");
    }

    private JsonNode suggestionsOf(String projectId) throws Exception {
        return body(mvc.perform(get(sourcingUrl(projectId) + "/suggestions")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn());
    }

    private void inTheUae(String projectId) {
        db.update("UPDATE app_lm_position SET location_country = 'United Arab Emirates' WHERE project_id = ?::uuid",
                projectId);
    }

    private static String criteriaJson(String functionTerms, String standaloneTitles, int perCompany) {
        return """
                {"levels":["C_LEVEL","SENIOR_VP","VP_HEAD_DIRECTOR"],"functionTerms":%s,"standaloneTitles":%s,
                 "localLevelWords":[],"excludedWords":["Assistant"],"suggestions":[],"roleSummary":null,
                 "countryCodes":["AE","SA","QA","KW","BH","OM"],"perCompany":%d}""".formatted(functionTerms,
                standaloneTitles, perCompany);
    }

    private static String idsJson(String[] ids) {
        return "[" + Arrays.stream(ids).map(id -> "\"" + id + "\"")
                .collect(Collectors.joining(",")) + "]";
    }

    private static long levelCount(JsonNode counts, String level) {
        for (JsonNode count : counts.get("levels")) {
            if (count.get("level").asText().equals(level)) {
                return count.get("count").asLong();
            }
        }
        throw new AssertionError("No count for " + level);
    }

    private static JsonNode companyIn(JsonNode companies, String triageCompanyId) {
        for (JsonNode company : companies) {
            if (company.get("triageCompanyId").asText().equals(triageCompanyId)) {
                return company;
            }
        }
        throw new AssertionError("No company " + triageCompanyId + " in " + companies);
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

    private static JsonNode progressFor(JsonNode progress, String triageCompanyId) {
        for (JsonNode entry : progress) {
            if (entry.get("triageCompanyId").asText().equals(triageCompanyId)) {
                return entry;
            }
        }
        throw new AssertionError("No progress for " + triageCompanyId + " in " + progress);
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

    private void suppress(String linkedinUrl) throws Exception {
        mvc.perform(post("/api/v1/workspace/suppressions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"linkedinUrl\":\"%s\"}".formatted(linkedinUrl)))
                .andExpect(status().isCreated());
    }

    private String anotherPosition() throws Exception {
        String clientId = body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customName\":\"Second Client\"}"))
                .andReturn()).get("id").asText();
        return body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"%s\",\"positionTitle\":\"Group COO\"}".formatted(clientId)))
                .andReturn()).get("id").asText();
    }

    /** Its own admin per firm, so one test can open two mandates in two workspaces. */
    private String mandate(String firmName) throws Exception {
        adminEmail = firmName.toLowerCase().replaceAll("[^a-z]", "") + "@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", adminEmail), firmName);
        adminToken = login(adminEmail);
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
