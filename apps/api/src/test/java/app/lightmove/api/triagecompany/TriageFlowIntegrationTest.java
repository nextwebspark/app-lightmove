package app.lightmove.api.triagecompany;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.ApolloUniverse;
import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingEmailSender;
import app.lightmove.api.strategy.model.CompanyRow;
import app.lightmove.api.triagecompany.repository.TriageCompanyWriter;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Taking companies out of the market into a mandate and moving them between triage stages — the write
 * half of Strategy and the read behind the triage screen.
 *
 * <p>Two behaviours carry most of the weight. A stored company is a <b>snapshot</b>, so a triage
 * decision survives its subject leaving the universe. And re-adding is <b>idempotent and
 * non-resurrecting</b>: a company the team already declined stays declined however many times someone
 * widens the filter and presses "Add all".
 */
@IntegrationTest
@Import(RecordingEmailSender.Config.class)
class TriageFlowIntegrationTest extends FlowTestSupport {

    @Autowired JdbcTemplate db;
    @Autowired TriageCompanyWriter writer;

    private ApolloUniverse universe;

    @BeforeEach
    void freshUniverse() {
        universe = new ApolloUniverse(db);
        universe.reset();
    }

    @Test
    @DisplayName("a fresh mandate's universe is empty, not the market")
    void freshMandateHasAnEmptyUniverse() throws Exception {
        String admin = adminOf("Universe Empty Firm");
        String projectId = project(admin);
        universe.company("a1", "ACWA Power").industry("oil & energy").employees(10).insert();

        // Triage stopped querying the market when Strategy took over discovery. A mandate nobody
        // has triaged shows nothing, rather than the whole market as it once did.
        mvc.perform(get(triageUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(0))
                .andExpect(jsonPath("$.counts.inUniverse").value(0));
    }

    @Test
    @DisplayName("adding a company stores a server-resolved snapshot and lands it in the universe")
    void addStoresASnapshot() throws Exception {
        String admin = adminOf("Universe Add Firm");
        String projectId = project(admin);
        universe.company("a1", "ACWA Power").industry("oil & energy").country("Saudi Arabia")
                .city("Riyadh").employees(3_000).revenue(6_000_000_000L)
                .website("https://acwapower.com").logo("https://cdn.example/acwa.png").insert();

        mvc.perform(post(triageUrl(projectId))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"apolloAccountId":"a1"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("inUniverse"))
                .andExpect(jsonPath("$.companyName").value("ACWA Power"))
                .andExpect(jsonPath("$.industry").value("oil & energy"))
                .andExpect(jsonPath("$.numEmployees").value(3000))
                .andExpect(jsonPath("$.logoUrl").value("https://cdn.example/acwa.png"));

        mvc.perform(get(triageUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.counts.inUniverse").value(1));
    }

    @Test
    @DisplayName("adding the same company twice is idempotent, not a duplicate or an error")
    void addIsIdempotent() throws Exception {
        String admin = adminOf("Universe Idempotent Firm");
        String projectId = project(admin);
        universe.company("a1", "ACWA Power").industry("oil & energy").employees(10).insert();

        String first = add(admin, projectId, "a1");
        String second = add(admin, projectId, "a1");

        // The button is on every row; a second click means the same thing as the first.
        assertThat(second).isEqualTo(first);
        mvc.perform(get(triageUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.totalCount").value(1));
    }

    @Test
    @DisplayName("a company the universe does not hold is rejected")
    void unknownCompanyRejected() throws Exception {
        String admin = adminOf("Universe Unknown Firm");
        String projectId = project(admin);

        MvcResult result = mvc.perform(post(triageUrl(projectId))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"apolloAccountId":"nope"}"""))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(codeOf(result)).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("a stored company survives leaving the universe")
    void storedCompanySurvivesAVanishedSource() throws Exception {
        String admin = adminOf("Universe Vanished Firm");
        String projectId = project(admin);
        universe.company("a1", "ACWA Power").industry("oil & energy").employees(10).insert();
        add(admin, projectId, "a1");

        db.update("DELETE FROM app_lm_apollo_companies WHERE apollo_account_id = 'a1'");

        // A triage decision that loses its subject when the pipeline reloads is worse than a stale row.
        mvc.perform(get(triageUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.companies[0].companyName").value("ACWA Power"));
    }

    @Test
    @DisplayName("triage moves a company between the tabs and keeps the counts right")
    void triageMovesBetweenTabs() throws Exception {
        String admin = adminOf("Universe Triage Firm");
        String projectId = project(admin);
        universe.company("a1", "ACWA Power").industry("oil & energy").employees(10).insert();
        universe.company("a2", "Masdar").industry("oil & energy").employees(10).insert();
        String acwa = add(admin, projectId, "a1");
        add(admin, projectId, "a2");

        mvc.perform(patch(triageUrl(projectId) + "/" + acwa)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"shortlisted"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("shortlisted"));

        // The counts travel with every page, because the sub-nav is visible on all of them.
        mvc.perform(get(triageUrl(projectId)).param("status", "shortlisted")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.companies[0].companyName").value("ACWA Power"))
                .andExpect(jsonPath("$.counts.inUniverse").value(1))
                .andExpect(jsonPath("$.counts.shortlisted").value(1))
                .andExpect(jsonPath("$.counts.declined").value(0));
    }

    @Test
    @DisplayName("a status change leaves the note alone, and an empty note clears it")
    void noteAndStatusAreIndependent() throws Exception {
        String admin = adminOf("Universe Note Firm");
        String projectId = project(admin);
        universe.company("a1", "ACWA Power").industry("oil & energy").employees(10).insert();
        String id = add(admin, projectId, "a1");

        patchUniverse(admin, projectId, id, """
                {"note":"Adjacency only — no upstream exposure"}""")
                .andExpect(jsonPath("$.note").value("Adjacency only — no upstream exposure"));

        // Moving to Declined must not silently clear the note explaining why.
        patchUniverse(admin, projectId, id, """
                {"status":"declined"}""")
                .andExpect(jsonPath("$.status").value("declined"))
                .andExpect(jsonPath("$.note").value("Adjacency only — no upstream exposure"));

        // Clearing is an explicit empty string, which is the one distinction the shape has to carry.
        patchUniverse(admin, projectId, id, """
                {"note":""}""")
                .andExpect(jsonPath("$.note").doesNotExist());
    }

    @Test
    @DisplayName("an unknown status is rejected rather than stored")
    void unknownStatusRejected() throws Exception {
        String admin = adminOf("Universe Bad Status Firm");
        String projectId = project(admin);
        universe.company("a1", "ACWA Power").industry("oil & energy").employees(10).insert();
        String id = add(admin, projectId, "a1");

        MvcResult result = mvc.perform(patch(triageUrl(projectId) + "/" + id)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"maybe"}"""))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(codeOf(result)).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("the insert itself ignores a company the mandate already holds")
    void insertIgnoresAHeldCompany() throws Exception {
        String admin = adminOf("Universe Conflict Firm");
        String projectId = project(admin);
        universe.company("a1", "Energy One").industry("oil & energy").employees(100).insert();
        universe.company("a2", "Energy Two").industry("oil & energy").employees(90).insert();

        List<CompanyRow> rows = List.of(
                new CompanyRow("a1", "Energy One", "oil & energy", "Saudi Arabia", "Riyadh",
                        100, null, null, null, null, null),
                new CompanyRow("a2", "Energy Two", "oil & energy", "Saudi Arabia", "Riyadh",
                        90, null, null, null, null, null));
        UUID project = UUID.fromString(projectId);
        UUID actor = db.queryForObject(
                "SELECT id FROM app_lm_user WHERE email = ?", UUID.class, "alok@" + domain);

        // Straight at the writer, past the service's read-first fast path — which is what a second
        // "Add all" click racing the first gets past too. Without ON CONFLICT the second call fails
        // the whole batch on app_lm_project_triage_company_uk.
        assertThat(writer.insertIgnoringHeld(project, actor, rows)).isEqualTo(2);
        assertThat(writer.insertIgnoringHeld(project, actor, rows)).isZero();

        mvc.perform(get(triageUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.totalCount").value(2));
    }

    @Test
    @DisplayName("bulk add takes everything the filter matches and reports what it did")
    void bulkAddTakesTheScope() throws Exception {
        String admin = adminOf("Universe Bulk Firm");
        String projectId = project(admin);
        universe.company("a1", "Energy One").industry("oil & energy").employees(100).insert();
        universe.company("a2", "Energy Two").industry("oil & energy").employees(90).insert();
        universe.company("a3", "Shop Three").industry("retail").employees(80).insert();
        putFilter(admin, projectId, """
                {"filter":{"industries":["oil & energy"],"marketSegments":[],"countries":[],
                           "employeeBands":[],"revenueBands":[]}}""");

        mvc.perform(post(triageUrl(projectId) + "/from-filter")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added").value(2))
                .andExpect(jsonPath("$.skipped").value(0));

        // The retail company was never in scope, so it is not in the universe.
        mvc.perform(get(triageUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.totalCount").value(2));
    }

    @Test
    @DisplayName("a filter matching more than the limit is refused whole, and writes nothing")
    void bulkAddRefusesAnOversizedScope() throws Exception {
        String admin = adminOf("Universe Oversized Firm");
        String projectId = project(admin);
        // One past the test profile's bulk-add-limit of 5.
        for (int index = 1; index <= 6; index++) {
            universe.company("a" + index, "Energy " + index).industry("oil & energy")
                    .employees(100 - index).insert();
        }
        putFilter(admin, projectId, """
                {"filter":{"industries":["oil & energy"],"marketSegments":[],"countries":[],
                           "employeeBands":[],"revenueBands":[]}}""");

        MvcResult refused = mvc.perform(post(triageUrl(projectId) + "/from-filter")
                        .header("Authorization", "Bearer " + admin))
                .andReturn();
        assertThat(refused.getResponse().getStatus()).isEqualTo(409);
        assertThat(codeOf(refused)).isEqualTo("BULK_ADD_SCOPE_TOO_LARGE");
        // The numbers are the point of the message: neither is anything the caller sent.
        assertThat(body(refused).get("detail").asText()).contains("6").contains("5");

        // Nothing partial. Taking the first five would silently decide which five the mandate got.
        mvc.perform(get(triageUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.totalCount").value(0))
                .andExpect(jsonPath("$.counts.inUniverse").value(0));
    }

    @Test
    @DisplayName("bulk add re-run skips what the mandate holds and never resurrects a declined company")
    void bulkAddDoesNotResurrectDeclined() throws Exception {
        String admin = adminOf("Universe Rerun Firm");
        String projectId = project(admin);
        universe.company("a1", "Energy One").industry("oil & energy").employees(100).insert();
        universe.company("a2", "Energy Two").industry("oil & energy").employees(90).insert();
        putFilter(admin, projectId, """
                {"filter":{"industries":["oil & energy"],"marketSegments":[],"countries":[],
                           "employeeBands":[],"revenueBands":[]}}""");

        String declinedId = add(admin, projectId, "a1");
        patchUniverse(admin, projectId, declinedId, """
                {"status":"declined"}""");

        mvc.perform(post(triageUrl(projectId) + "/from-filter")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.added").value(1))
                .andExpect(jsonPath("$.skipped").value(1));

        // Re-running after widening a filter must not quietly undo a decision the team already made.
        mvc.perform(get(triageUrl(projectId)).param("status", "declined")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.companies[0].companyName").value("Energy One"));
    }

    @Test
    @DisplayName("bulk add honours the off-limits bar")
    void bulkAddHonoursOffLimits() throws Exception {
        String admin = adminOf("Universe Bulk Off Limits Firm");
        String projectId = project(admin);
        universe.company("a1", "Energy One").industry("oil & energy").employees(100).insert();
        universe.company("a2", "Barred Two").industry("oil & energy").employees(90).insert();
        mvc.perform(put(strategyUrl(projectId) + "/off-limits")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"apolloAccountIds":["a2"]}"""))
                .andExpect(status().isOk());

        mvc.perform(post(triageUrl(projectId) + "/from-filter")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.added").value(1));
        mvc.perform(get(triageUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.companies[0].companyName").value("Energy One"));
    }

    @Test
    @DisplayName("another mandate's universe row cannot be triaged through this one")
    void anotherMandatesRowIsNotFound() throws Exception {
        String admin = adminOf("Universe Isolation Firm");
        String owner = project(admin);
        String other = project(admin);
        universe.company("a1", "ACWA Power").industry("oil & energy").employees(10).insert();
        String id = add(admin, owner, "a1");

        mvc.perform(patch(triageUrl(other) + "/" + id)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"declined"}"""))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("an unknown triage stage and an over-large page are rejected")
    void triageListGuards() throws Exception {
        String admin = adminOf("Universe Guard Firm");
        String projectId = project(admin);

        MvcResult badStatus = mvc.perform(get(triageUrl(projectId)).param("status", "maybe")
                        .header("Authorization", "Bearer " + admin))
                .andReturn();
        assertThat(badStatus.getResponse().getStatus()).isEqualTo(400);

        MvcResult tooBig = mvc.perform(get(triageUrl(projectId)).param("size", "5000")
                        .header("Authorization", "Bearer " + admin))
                .andReturn();
        assertThat(tooBig.getResponse().getStatus()).isEqualTo(400);
    }

    private String add(String token, String projectId, String accountId) throws Exception {
        return body(mvc.perform(post(triageUrl(projectId))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"apolloAccountId":"%s"}""".formatted(accountId)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private org.springframework.test.web.servlet.ResultActions patchUniverse(String token,
                                                                             String projectId,
                                                                             String universeCompanyId,
                                                                             String bodyJson)
            throws Exception {
        return mvc.perform(patch(triageUrl(projectId) + "/" + universeCompanyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyJson))
                .andExpect(status().isOk());
    }

    private void putFilter(String token, String projectId, String bodyJson) throws Exception {
        mvc.perform(put(strategyUrl(projectId) + "/filter")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyJson))
                .andExpect(status().isOk());
    }

    private static String strategyUrl(String projectId) {
        return "/api/v1/projects/" + projectId + "/strategy";
    }

    private static String triageUrl(String projectId) {
        return "/api/v1/projects/" + projectId + "/triage";
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
                                {"customName":"Universe Client %s"}"""
                                .formatted(java.util.UUID.randomUUID())))
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
