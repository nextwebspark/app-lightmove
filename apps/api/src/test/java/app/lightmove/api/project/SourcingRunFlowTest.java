package app.lightmove.api.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingCoreSignalGateway;
import app.lightmove.api.RecordingEmailSender;
import app.lightmove.api.company.service.CoreSignalCompanyCache;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

/**
 * The CoreSignal sourcing run end to end against the recording gateway: start → search → parallel
 * collect → READY with revenue-desc-ordered, tier-classified companies; the collect cache as the
 * credit safeguard (the flagship assertion: a repeat run spends nothing); extend, failure/retry,
 * staleness on strategy edits, and the run endpoints' auth gates.
 *
 * <p>The executor is {@code @Async}, so terminal states are awaited by polling the real endpoint —
 * which doubles as a test of what the SPA's poll loop actually sees.
 */
@IntegrationTest
@Import({RecordingEmailSender.Config.class, RecordingCoreSignalGateway.Config.class})
class SourcingRunFlowTest extends FlowTestSupport {

    @Autowired RecordingCoreSignalGateway coresignal;
    @Autowired CoreSignalCompanyCache cache;
    @Autowired JdbcTemplate db;

    @BeforeEach
    void freshProvider() {
        coresignal.reset();
        // The collect cache is deliberately global (shared across workspaces); clear it so each
        // test's "was this collected?" assertions see only its own spend.
        db.execute("DELETE FROM app_lm_coresignal_company");
    }

    @Test
    @DisplayName("a run searches once, collects in parallel, and serves companies in search order with tiers")
    void runCompletesWithOrderedTieredCompanies() throws Exception {
        String admin = adminOf("CoreSignal Run Firm");
        String projectId = project(admin);
        putSectors(admin, projectId, """
                {"direct":[{"label":"Retail","selected":true}],
                 "adjacent":[{"label":"Wholesale","selected":true}],
                 "inferred":[{"label":"Grocery Retail","selected":true}]}""");

        coresignal.givenSearch(List.of(30L, 10L, 20L), 138);
        coresignal.givenCompany(RecordingCoreSignalGateway.company(30L, "Rich Retail", "Retail", 90_000_000));
        coresignal.givenCompany(RecordingCoreSignalGateway.company(10L, "Mid Wholesale", "Wholesale", 50_000_000));
        coresignal.givenCompany(RecordingCoreSignalGateway.company(20L, "Odd Industry", "Shipbuilding", 10_000_000));

        mvc.perform(post(runsUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(status().isAccepted());
        JsonNode run = pollUntil(admin, projectId, "READY");

        assertThat(run.get("totalMatched").asLong()).isEqualTo(138);
        assertThat(run.get("collectedCount").asInt()).isEqualTo(3);
        assertThat(run.get("criteriaMatchesStrategy").asBoolean()).isTrue();
        JsonNode companies = run.get("companies");
        // Search order (the provider's revenue-desc) is preserved verbatim, never re-sorted here.
        assertThat(companies.get(0).get("name").asText()).isEqualTo("Rich Retail");
        assertThat(companies.get(0).get("matchTier").asText()).isEqualTo("DIRECT");
        assertThat(companies.get(1).get("name").asText()).isEqualTo("Mid Wholesale");
        assertThat(companies.get(1).get("matchTier").asText()).isEqualTo("ADJACENT");
        assertThat(companies.get(2).get("name").asText()).isEqualTo("Odd Industry");
        assertThat(companies.get(2).get("matchTier").asText()).isEqualTo("INFERRED");
        // The drawer's fields ride the same response.
        assertThat(companies.get(0).get("website").asText()).isEqualTo("https://30.example");
        assertThat(companies.get(0).get("linkedinUrl").asText()).contains("linkedin.com/company/");
        assertThat(companies.get(0).get("logoUrl").asText()).contains("logo.example");
        assertThat(companies.get(0).get("description").asText()).isNotBlank();
        assertThat(coresignal.searchCalls()).isEqualTo(1);
        assertThat(coresignal.collectedIds()).containsExactlyInAnyOrder(30L, 10L, 20L);
    }

    @Test
    @DisplayName("re-starting with unchanged criteria answers from the stored run — zero provider calls")
    void repeatStartSpendsNothing() throws Exception {
        String admin = adminOf("CoreSignal Idempotent Firm");
        String projectId = project(admin);
        putSectors(admin, projectId, """
                {"direct":[{"label":"Retail","selected":true}],"adjacent":[],"inferred":[]}""");
        coresignal.givenSearch(List.of(1L), 1);
        coresignal.givenCompany(RecordingCoreSignalGateway.company(1L, "Only Retail", "Retail", 1_000_000));

        mvc.perform(post(runsUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(status().isAccepted());
        pollUntil(admin, projectId, "READY");
        int searches = coresignal.searchCalls();
        int collects = coresignal.collectedIds().size();

        JsonNode rerun = body(mvc.perform(post(runsUrl(projectId))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isAccepted())
                .andReturn()).get("run");

        assertThat(rerun.get("status").asText()).isEqualTo("READY");
        assertThat(coresignal.searchCalls()).isEqualTo(searches);
        assertThat(coresignal.collectedIds()).hasSize(collects);
    }

    @Test
    @DisplayName("an already-cached company is served without a collect call — the credit safeguard")
    void cachedCompanyNeverRecollected() throws Exception {
        String admin = adminOf("CoreSignal Cache Firm");
        String projectId = project(admin);
        putSectors(admin, projectId, """
                {"direct":[{"label":"Retail","selected":true}],"adjacent":[],"inferred":[]}""");

        // Another project (any tenant) paid for company 7 earlier; the cache remembers.
        cache.store(RecordingCoreSignalGateway.company(7L, "Prepaid Retail", "Retail", 9_000_000));
        coresignal.givenSearch(List.of(7L, 8L), 2);
        coresignal.givenCompany(RecordingCoreSignalGateway.company(8L, "Fresh Retail", "Retail", 3_000_000));

        mvc.perform(post(runsUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(status().isAccepted());
        JsonNode run = pollUntil(admin, projectId, "READY");

        assertThat(run.get("collectedCount").asInt()).isEqualTo(2);
        assertThat(run.get("companies").get(0).get("name").asText()).isEqualTo("Prepaid Retail");
        assertThat(coresignal.collectedIds()).containsExactly(8L); // 7 was cached — never re-billed
    }

    @Test
    @DisplayName("an id CoreSignal no longer knows is skipped; the run still completes with the rest")
    void vanishedIdSkipped() throws Exception {
        String admin = adminOf("CoreSignal Vanished Firm");
        String projectId = project(admin);
        putSectors(admin, projectId, """
                {"direct":[{"label":"Retail","selected":true}],"adjacent":[],"inferred":[]}""");
        coresignal.givenSearch(List.of(1L, 2L), 2);
        coresignal.givenCompany(RecordingCoreSignalGateway.company(1L, "Alive Retail", "Retail", 1_000_000));
        // No record programmed for id 2 — the gateway answers empty, CoreSignal's 404.

        mvc.perform(post(runsUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(status().isAccepted());
        JsonNode run = pollUntil(admin, projectId, "READY");

        assertThat(run.get("collectedCount").asInt()).isEqualTo(1);
        assertThat(run.get("companies").get(0).get("name").asText()).isEqualTo("Alive Retail");
    }

    @Test
    @DisplayName("a provider failure fails the run with its detail; a retry after recovery restarts it")
    void searchFailureFailsRunAndRetryRestarts() throws Exception {
        String admin = adminOf("CoreSignal Failure Firm");
        String projectId = project(admin);
        putSectors(admin, projectId, """
                {"direct":[{"label":"Retail","selected":true}],"adjacent":[],"inferred":[]}""");
        coresignal.failSearch("CoreSignal search failed: out of credits");

        mvc.perform(post(runsUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(status().isAccepted());
        JsonNode failed = pollUntil(admin, projectId, "FAILED");
        assertThat(failed.get("error").asText()).contains("out of credits");

        coresignal.searchSucceedsAgain();
        coresignal.givenSearch(List.of(1L), 1);
        coresignal.givenCompany(RecordingCoreSignalGateway.company(1L, "Back Retail", "Retail", 1_000_000));

        mvc.perform(post(runsUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(status().isAccepted());
        JsonNode recovered = pollUntil(admin, projectId, "READY");
        assertThat(recovered.get("companies").get(0).get("name").asText()).isEqualTo("Back Retail");
    }

    @Test
    @DisplayName("extend collects exactly the next batch of the already-searched ids")
    void extendCollectsNextBatchOnly() throws Exception {
        String admin = adminOf("CoreSignal Extend Firm");
        String projectId = project(admin);
        putSectors(admin, projectId, """
                {"direct":[{"label":"Retail","selected":true}],"adjacent":[],"inferred":[]}""");

        List<Long> ids = LongStream.rangeClosed(1, 30).boxed().toList();
        coresignal.givenSearch(ids, 30);
        ids.forEach(id -> coresignal.givenCompany(
                RecordingCoreSignalGateway.company(id, "Retail " + id, "Retail", 1_000_000 * id)));

        mvc.perform(post(runsUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(status().isAccepted());
        JsonNode first = pollUntil(admin, projectId, "READY");
        assertThat(first.get("requestedCount").asInt()).isEqualTo(25); // the configured batch size
        assertThat(first.get("collectedCount").asInt()).isEqualTo(25);
        assertThat(coresignal.searchCalls()).isEqualTo(1);

        mvc.perform(post(runsUrl(projectId) + "/current/extend")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isAccepted());
        JsonNode extended = pollUntil(admin, projectId, "READY");

        assertThat(extended.get("requestedCount").asInt()).isEqualTo(30);
        assertThat(extended.get("collectedCount").asInt()).isEqualTo(30);
        assertThat(coresignal.searchCalls()).isEqualTo(1); // extend never re-searches
        assertThat(coresignal.collectedIds()).hasSize(30); // and the first 25 were not re-billed
    }

    @Test
    @DisplayName("editing the strategy flags the stored run stale; the next start replaces it")
    void strategyEditFlagsStaleAndRestartReplaces() throws Exception {
        String admin = adminOf("CoreSignal Stale Firm");
        String projectId = project(admin);
        putSectors(admin, projectId, """
                {"direct":[{"label":"Retail","selected":true}],"adjacent":[],"inferred":[]}""");
        coresignal.givenSearch(List.of(1L), 1);
        coresignal.givenCompany(RecordingCoreSignalGateway.company(1L, "Old Scope Retail", "Retail", 1_000_000));

        mvc.perform(post(runsUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(status().isAccepted());
        pollUntil(admin, projectId, "READY");

        putSectors(admin, projectId, """
                {"direct":[{"label":"Oil and Gas","selected":true}],"adjacent":[],"inferred":[]}""");
        JsonNode stale = body(mvc.perform(get(currentUrl(projectId))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn()).get("run");
        assertThat(stale.get("criteriaMatchesStrategy").asBoolean()).isFalse();

        coresignal.givenSearch(List.of(2L), 1);
        coresignal.givenCompany(RecordingCoreSignalGateway.company(2L, "Gulf Energy", "Oil and Gas", 5_000_000));
        mvc.perform(post(runsUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(status().isAccepted());
        JsonNode replaced = pollUntil(admin, projectId, "READY");

        assertThat(replaced.get("criteriaMatchesStrategy").asBoolean()).isTrue();
        assertThat(replaced.get("companies").get(0).get("name").asText()).isEqualTo("Gulf Energy");
        assertThat(coresignal.searchCalls()).isEqualTo(2);
    }

    @Test
    @DisplayName("an anchorless scope goes straight to READY-empty without a single provider call")
    void anchorlessScopeSpendsNothing() throws Exception {
        String admin = adminOf("CoreSignal Anchorless Firm");
        String projectId = project(admin);
        // No strategy at all — no sectors, no tags: nothing to anchor a search on.

        mvc.perform(post(runsUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(status().isAccepted());
        JsonNode run = pollUntil(admin, projectId, "READY");

        assertThat(run.get("collectedCount").asInt()).isZero();
        assertThat(run.get("totalMatched").asLong()).isZero();
        assertThat(coresignal.searchCalls()).isZero();
        assertThat(coresignal.collectedIds()).isEmpty();
    }

    @Test
    @DisplayName("polling never 404s before the first run — a null run tells the SPA to start one")
    void pollBeforeAnyRunReturnsNull() throws Exception {
        String admin = adminOf("CoreSignal NoRun Firm");
        String projectId = project(admin);

        JsonNode response = body(mvc.perform(get(currentUrl(projectId))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(response.get("run").isNull()).isTrue();
    }

    @Test
    @DisplayName("run gates: unseated member is shut out, a seated researcher can start, outsiders see 404")
    void runEndpointsEnforceProjectGates() throws Exception {
        String alok = "alok@" + domain;
        String sara = "sara@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "CoreSignal Gate Firm");
        String admin = login(alok);
        inviteAndAccept(admin, "Sara Al-Mansour", sara, "MEMBER");
        String projectId = project(admin);
        String saraToken = login(sara);

        // Unseated member: authenticated, but no seat on this mandate.
        mvc.perform(get(currentUrl(projectId)).header("Authorization", "Bearer " + saraToken))
                .andExpect(status().isForbidden());
        mvc.perform(post(runsUrl(projectId)).header("Authorization", "Bearer " + saraToken))
                .andExpect(status().isForbidden());

        // Seated researcher: WORK_VIEW and WORK_EXECUTE both in hand.
        seat(admin, projectId, memberIdOf(admin, sara), "[\"RESEARCHER\"]");
        String saraSeated = login(sara);
        mvc.perform(get(currentUrl(projectId)).header("Authorization", "Bearer " + saraSeated))
                .andExpect(status().isOk());
        mvc.perform(post(runsUrl(projectId)).header("Authorization", "Bearer " + saraSeated))
                .andExpect(status().isAccepted());

        // A stranger from another workspace: the project's existence is masked, not refused.
        String outsider = verifiedUser("Out Sider", "out@other-" + domain);
        mvc.perform(get(currentUrl(projectId)).header("Authorization", "Bearer " + outsider))
                .andExpect(status().isNotFound());
        mvc.perform(post(runsUrl(projectId)).header("Authorization", "Bearer " + outsider))
                .andExpect(status().isNotFound());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static String runsUrl(String projectId) {
        return "/api/v1/projects/" + projectId + "/sourcing/runs";
    }

    private static String currentUrl(String projectId) {
        return runsUrl(projectId) + "/current";
    }

    /** Poll the real endpoint (what the SPA does) until the run reaches the wanted terminal status. */
    private JsonNode pollUntil(String token, String projectId, String status) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            JsonNode run = body(mvc.perform(get(currentUrl(projectId))
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn()).get("run");
            if (run != null && !run.isNull() && status.equals(run.get("status").asText())) {
                return run;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Run never reached status " + status + " within 10s");
    }

    private void putSectors(String token, String projectId, String bodyJson) throws Exception {
        mvc.perform(put("/api/v1/projects/" + projectId + "/strategy/sectors")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyJson))
                .andExpect(status().isOk());
    }

    private void seat(String adminToken, String projectId, String memberId, String rolesJson)
            throws Exception {
        mvc.perform(put("/api/v1/projects/" + projectId + "/members/" + memberId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roles":%s}""".formatted(rolesJson)))
                .andExpect(status().isOk());
    }

    private String adminOf(String workspaceName) throws Exception {
        createWorkspace(verifiedUser("Alok Kumar", "alok@" + domain), workspaceName);
        return login("alok@" + domain);
    }

    private String project(String token) throws Exception {
        String clientId = body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"CoreSignal Client"}"""))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
        return body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"Head of Retail"}
                                """.formatted(clientId)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }
}
