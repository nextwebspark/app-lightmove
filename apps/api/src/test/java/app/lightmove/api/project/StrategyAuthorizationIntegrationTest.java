package app.lightmove.api.project;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingEmailSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;

/**
 * The strategy's action matrix: reading the scope needs a seat (WORK_EXECUTE, held by every project
 * role), so an unseated member is shut out and any seated researcher gets in; writing it needs
 * PROJECT_EDIT on the seat — a researcher cannot, a lead can.
 */
@IntegrationTest
@Import(RecordingEmailSender.Config.class)
class StrategyAuthorizationIntegrationTest extends FlowTestSupport {

    private static final String SNAPSHOT = """
            {"direct":[{"label":"Retail","selected":true}],"adjacent":[],"inferred":[]}""";

    @Test
    @DisplayName("an unseated member can neither read nor write the scope")
    void unseatedMemberCannotRead() throws Exception {
        Fixture f = fixture("Strategy Unseated Firm");
        String sara = login(f.saraEmail);

        mvc.perform(get(strategyUrl(f.projectId)).header("Authorization", "Bearer " + sara))
                .andExpect(status().isForbidden());

        mvc.perform(put(sectorsUrl(f.projectId))
                        .header("Authorization", "Bearer " + sara)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SNAPSHOT))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a seated researcher reads the scope but does not shape it")
    void researcherReadsButCannotWrite() throws Exception {
        Fixture f = fixture("Strategy Researcher Firm");
        seat(f.admin, f.projectId, f.saraId, "[\"RESEARCHER\"]");
        String sara = login(f.saraEmail);

        mvc.perform(get(strategyUrl(f.projectId)).header("Authorization", "Bearer " + sara))
                .andExpect(status().isOk());

        mvc.perform(put(sectorsUrl(f.projectId))
                        .header("Authorization", "Bearer " + sara)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SNAPSHOT))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a lead with PROJECT_EDIT can write the scope")
    void leadCanWrite() throws Exception {
        Fixture f = fixture("Strategy Lead Firm");
        seat(f.admin, f.projectId, f.saraId, "[\"LEAD\"]");
        String sara = login(f.saraEmail);

        mvc.perform(put(sectorsUrl(f.projectId))
                        .header("Authorization", "Bearer " + sara)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SNAPSHOT))
                .andExpect(status().isOk());
    }

    // ── fixture ──────────────────────────────────────────────────────────────

    private static String strategyUrl(String projectId) {
        return "/api/v1/projects/" + projectId + "/strategy";
    }

    private static String sectorsUrl(String projectId) {
        return strategyUrl(projectId) + "/sectors";
    }

    private record Fixture(String admin, String projectId, String saraEmail, String saraId) {}

    private Fixture fixture(String firmName) throws Exception {
        String alok = "alok@" + domain;
        String sara = "sara@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), firmName);
        String admin = login(alok);
        inviteAndAccept(admin, "Sara Al-Mansour", sara, "MEMBER");

        String clientId = body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Matrix Client"}"""))
                .andReturn()).get("id").asText();
        String projectId = body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"Head of Retail"}
                                """.formatted(clientId)))
                .andReturn()).get("id").asText();

        return new Fixture(admin, projectId, sara, memberIdOf(admin, sara));
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
}
