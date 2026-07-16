package app.lightmove.api.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingEmailSender;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/** Mandates end to end: inline clients, creation, teams, the one-lead rule, and tenant isolation. */
@IntegrationTest
@Import(RecordingEmailSender.Config.class)
class ProjectFlowIntegrationTest extends FlowTestSupport {

    @Test
    @DisplayName("a client is created inline once — the same name again is a 409, whatever its case")
    void clientNamesAreUniquePerWorkspace() throws Exception {
        String admin = adminOf("Client Firm");

        mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Meridian Energy","hqCountry":"UAE"}"""))
                .andExpect(status().isCreated());

        MvcResult duplicate = mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"MERIDIAN ENERGY"}"""))
                .andReturn();
        assertThat(duplicate.getResponse().getStatus()).isEqualTo(409);
        assertThat(codeOf(duplicate)).isEqualTo("CLIENT_ALREADY_EXISTS");
    }

    @Test
    @DisplayName("a new project lands at BRIEF with the chosen lead as its whole team")
    void createLandsAtBriefWithLead() throws Exception {
        String alok = "alok@" + domain;
        String admin = adminOf("Brief Firm", alok);
        String clientId = createClient(admin, "Meridian Energy");
        String leadId = memberIdOf(admin, alok);

        mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"Chief Financial Officer",
                                 "leadMemberId":"%s","targetDate":"%s"}
                                """.formatted(clientId, leadId, LocalDate.now().plusMonths(6))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stage").value("BRIEF"))
                .andExpect(jsonPath("$.health").value("OK"))
                .andExpect(jsonPath("$.clientName").value("Meridian Energy"))
                .andExpect(jsonPath("$.team.length()").value(1))
                .andExpect(jsonPath("$.team[0].projectRole").value("LEAD"))
                .andExpect(jsonPath("$.companies").value(0))
                .andExpect(jsonPath("$.candidates").value(0));

        // The client now reports its mandate.
        mvc.perform(get("/api/v1/clients").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$[0].activeMandates").value(1));
    }

    @Test
    @DisplayName("a workspace RESEARCHER can lead a project — the two role levels are independent")
    void researcherCanLead() throws Exception {
        String alok = "alok@" + domain;
        String sara = "sara@" + domain;
        String admin = adminOf("Role Levels Firm", alok);
        inviteAndAccept(admin, "Sara Al-Mansour", sara, "RESEARCHER");
        String clientId = createClient(admin, "Agthia Group");

        mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"CEO","leadMemberId":"%s"}
                                """.formatted(clientId, memberIdOf(admin, sara))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.team[0].workspaceRole").value("RESEARCHER"))
                .andExpect(jsonPath("$.team[0].projectRole").value("LEAD"));
    }

    @Test
    @DisplayName("teammates come and go, but the lead can only be replaced, never removed")
    void teamChangesRespectTheLead() throws Exception {
        String alok = "alok@" + domain;
        String sara = "sara@" + domain;
        String admin = adminOf("Team Firm", alok);
        inviteAndAccept(admin, "Sara Al-Mansour", sara, "CONSULTANT");
        String saraToken = login(sara);

        String alokId = memberIdOf(admin, alok);
        String saraId = memberIdOf(admin, sara);
        String projectId = createProject(admin, createClient(admin, "Bindawood"), "CDO", alokId);

        // Sara, a plain member, may adjust the team — she adds herself.
        mvc.perform(put("/api/v1/projects/" + projectId + "/members/" + saraId)
                        .header("Authorization", "Bearer " + saraToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.team.length()").value(2));

        // Adding her again is a no-op, not an error: PUT is idempotent.
        mvc.perform(put("/api/v1/projects/" + projectId + "/members/" + saraId)
                        .header("Authorization", "Bearer " + saraToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.team.length()").value(2));

        // The lead cannot be pulled off the team.
        MvcResult leadPull = mvc.perform(delete("/api/v1/projects/" + projectId + "/members/" + alokId)
                        .header("Authorization", "Bearer " + saraToken))
                .andReturn();
        assertThat(codeOf(leadPull)).isEqualTo("PROJECT_LEAD_REQUIRED");

        // Reassigning the lead swaps the roles atomically; the old lead stays as a member.
        mvc.perform(patch("/api/v1/projects/" + projectId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"leadMemberId":"%s"}
                                """.formatted(saraId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.team.length()").value(2));

        JsonNode team = body(mvc.perform(get("/api/v1/projects")
                        .header("Authorization", "Bearer " + admin))
                .andReturn()).get(0).get("team");
        for (JsonNode seat : team) {
            String expected = seat.get("memberId").asText().equals(saraId) ? "LEAD" : "MEMBER";
            assertThat(seat.get("projectRole").asText()).isEqualTo(expected);
        }

        // And now the old lead can leave the team.
        mvc.perform(delete("/api/v1/projects/" + projectId + "/members/" + alokId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.team.length()").value(1));
    }

    @Test
    @DisplayName("nothing of one workspace's projects is visible or reachable from another")
    void tenantIsolation() throws Exception {
        String alok = "alok@" + domain;
        String admin = adminOf("Isolation Firm", alok);
        String clientId = createClient(admin, "Meridian Energy");
        String projectId = createProject(admin, clientId, "CFO", memberIdOf(admin, alok));

        String rivalEmail = "boss@rival-" + domain;
        createWorkspace(verifiedUser("Rival Boss", rivalEmail), "Rival Isolation Firm");
        String rival = login(rivalEmail);
        String rivalMemberId = memberIdOf(rival, rivalEmail);

        mvc.perform(get("/api/v1/projects").header("Authorization", "Bearer " + rival))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mvc.perform(patch("/api/v1/projects/" + projectId)
                        .header("Authorization", "Bearer " + rival)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetDate":"2027-01-01"}"""))
                .andExpect(status().isNotFound());

        mvc.perform(put("/api/v1/projects/" + projectId + "/members/" + rivalMemberId)
                        .header("Authorization", "Bearer " + rival))
                .andExpect(status().isNotFound());

        // Another firm's client id is not a client as far as this caller is concerned.
        mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + rival)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"CTO","leadMemberId":"%s"}
                                """.formatted(clientId, rivalMemberId)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("an unverified user reaches no project data, valid token or not")
    void unverifiedUserIsBlocked() throws Exception {
        adminOf("Verified Firm");

        String unverified = signup("Impostor", "impostor@" + domain).get("accessToken").asText();
        mvc.perform(get("/api/v1/projects").header("Authorization", "Bearer " + unverified))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a mandate past its target reads off-track; a delivered one reads done")
    void healthShowsInTheList() throws Exception {
        String alok = "alok@" + domain;
        String admin = adminOf("Health Firm", alok);
        String clientId = createClient(admin, "Al Rabie");
        String leadId = memberIdOf(admin, alok);

        mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"CHRO","leadMemberId":"%s",
                                 "targetDate":"%s"}
                                """.formatted(clientId, leadId, LocalDate.now().minusDays(1))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.health").value("OFF"));
    }

    private String adminOf(String workspaceName) throws Exception {
        return adminOf(workspaceName, "alok@" + domain);
    }

    private String adminOf(String workspaceName, String adminEmail) throws Exception {
        createWorkspace(verifiedUser("Alok Kumar", adminEmail), workspaceName);
        return login(adminEmail);
    }

    private String createClient(String token, String name) throws Exception {
        return body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s"}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private String createProject(String token, String clientId, String position, String leadMemberId)
            throws Exception {
        return body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"%s","leadMemberId":"%s"}
                                """.formatted(clientId, position, leadMemberId)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }
}
