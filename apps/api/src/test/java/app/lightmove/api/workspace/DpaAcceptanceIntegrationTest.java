package app.lightmove.api.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FixtureJdbc;
import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * #928's data processing agreement, in a deployment that names one: both doors that found a workspace ask for the
 * tick, record who accepted which version, and editing the organisation step never asks again.
 */
@IntegrationTest
@TestPropertySource(properties = {
        "lightmove.legal.dpa-version=2026-11-01",
        "lightmove.legal.dpa-url=https://uncava.com/dpa",
})
class DpaAcceptanceIntegrationTest extends FlowTestSupport {

    @Autowired @FixtureJdbc JdbcTemplate fixtures;

    @Test
    @DisplayName("the onboarding step is told which agreement to show")
    void theAgreementIsOffered() throws Exception {
        String token = verifiedUser("Yara Haddad", "yara@" + domain);

        JsonNode legal = body(mvc.perform(get("/api/v1/onboarding/legal").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn());

        assertThat(legal.get("dpaVersion").asText()).isEqualTo("2026-11-01");
        assertThat(legal.get("dpaUrl").asText()).isEqualTo("https://uncava.com/dpa");
    }

    @Test
    @DisplayName("signup's step founds nothing without the tick, and records the version, the founder and the time with it")
    void signupRecordsTheAgreement() throws Exception {
        String address = "omar@" + domain;
        String token = verifiedUser("Omar Khalil", address);

        MvcResult refused = found("/api/v1/onboarding/workspace", token, "Unticked Firm", false);
        assertThat(refused.getResponse().getStatus()).isEqualTo(400);
        assertThat(codeOf(refused)).isEqualTo("DPA_ACCEPTANCE_REQUIRED");
        assertThat(fixtures.queryForObject("SELECT count(*) FROM app_lm_workspace WHERE name = 'Unticked Firm'",
                Integer.class)).isZero();

        MvcResult founded = found("/api/v1/onboarding/workspace", token, "Ticked Firm", true);
        assertThat(founded.getResponse().getStatus()).as(founded.getResponse().getContentAsString()).isEqualTo(201);
        String workspaceId = body(founded).at("/workspace/id").asText();

        Map<String, Object> row = fixtures.queryForMap("""
                SELECT w.dpa_version, w.dpa_accepted_at, u.email AS accepted_by FROM app_lm_workspace w
                JOIN app_lm_user u ON u.id = w.dpa_accepted_by WHERE w.id = ?::uuid
                """, workspaceId);
        assertThat(row.get("dpa_version")).isEqualTo("2026-11-01");
        assertThat(row.get("dpa_accepted_at")).isNotNull();
        assertThat(row.get("accepted_by")).isEqualTo(address);
        assertThat(fixtures.queryForObject("""
                SELECT metadata ->> 'dpaVersion' FROM app_lm_audit_event
                WHERE event_type = 'WORKSPACE_CREATED' AND workspace_id = ?::uuid
                """, String.class, workspaceId)).isEqualTo("2026-11-01");

        // Signup's Back edits the step it founded: the agreement is not asked again.
        String admin = login(address);
        mvc.perform(patch("/api/v1/onboarding/workspace")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mode":"AGENCY","name":"Ticked Firm Renamed"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a further workspace founded from Settings asks for the same tick")
    void settingsAsksToo() throws Exception {
        String address = "nadia@" + domain;
        String token = verifiedUser("Nadia Saleh", address);
        found("/api/v1/onboarding/workspace", token, "First Firm", true);
        String staff = login(address);

        MvcResult refused = found("/api/v1/workspaces", staff, "Second Firm", false);
        assertThat(codeOf(refused)).isEqualTo("DPA_ACCEPTANCE_REQUIRED");

        MvcResult founded = found("/api/v1/workspaces", staff, "Second Firm", true);
        assertThat(founded.getResponse().getStatus()).as(founded.getResponse().getContentAsString()).isEqualTo(201);
    }

    private MvcResult found(String path, String token, String name, boolean dpaAccepted) throws Exception {
        return mvc.perform(post(path)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "mode", "COMPANY", "name", name, "dpaAccepted", dpaAccepted))))
                .andReturn();
    }
}
