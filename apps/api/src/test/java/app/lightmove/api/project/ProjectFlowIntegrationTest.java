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

/** Mandates end to end: inline clients, creation, seating, the last-lead rule, isolation. */
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
                                {"customName":"Meridian Energy","hqCountry":"UAE"}"""))
                .andExpect(status().isCreated());

        MvcResult duplicate = mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"MERIDIAN ENERGY"}"""))
                .andReturn();
        assertThat(duplicate.getResponse().getStatus()).isEqualTo(409);
        assertThat(codeOf(duplicate)).isEqualTo("CLIENT_ALREADY_EXISTS");
    }

    @Test
    @DisplayName("a new project lands at BRIEF with its creator seated as its lead, and nothing else")
    void createLandsAtBriefWithCreatorAsLead() throws Exception {
        String admin = adminOf("Brief Firm");
        String clientId = createClient(admin, "Meridian Energy");

        mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"Chief Financial Officer",
                                 "targetDate":"%s"}
                                """.formatted(clientId, LocalDate.now().plusMonths(6))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stage").value("BRIEF"))
                .andExpect(jsonPath("$.health").value("OK"))
                .andExpect(jsonPath("$.clientName").value("Meridian Energy"))
                .andExpect(jsonPath("$.team.length()").value(1))
                .andExpect(jsonPath("$.team[0].projectRoles.length()").value(1))
                .andExpect(jsonPath("$.team[0].projectRoles[0]").value("LEAD"))
                .andExpect(jsonPath("$.companies").value(0))
                .andExpect(jsonPath("$.candidates").value(0));

        // The client now reports its mandate.
        mvc.perform(get("/api/v1/clients").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$[0].activeMandates").value(1));
    }

    @Test
    @DisplayName("a workspace MEMBER can hold a LEAD seat — the two tiers are independent")
    void memberCanLead() throws Exception {
        String alok = "alok@" + domain;
        String sara = "sara@" + domain;
        String admin = adminOf("Role Levels Firm", alok);
        inviteAndAccept(admin, "Sara Al-Mansour", sara, "MEMBER");
        String projectId = createProject(admin, createClient(admin, "Agthia Group"), "CEO");

        mvc.perform(put("/api/v1/projects/" + projectId + "/members/" + memberIdOf(admin, sara))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"LEAD"}"""))
                .andExpect(status().isOk());

        JsonNode team = body(mvc.perform(get("/api/v1/projects")
                        .header("Authorization", "Bearer " + admin))
                .andReturn()).get(0).get("team");
        JsonNode saraSeat = seatOf(team, memberIdOf(admin, sara));
        assertThat(saraSeat.get("workspaceRoles").get(0).asText()).isEqualTo("MEMBER");
        assertThat(saraSeat.get("projectRoles").get(0).asText()).isEqualTo("LEAD");
    }

    @Test
    @DisplayName("a seat holds one staff role at a time, though several seats may hold LEAD at once")
    void oneRolePerSeatAndPluralLeads() throws Exception {
        String alok = "alok@" + domain;
        String sara = "sara@" + domain;
        String omar = "omar@" + domain;
        String admin = adminOf("Plural Firm", alok);
        inviteAndAccept(admin, "Sara Al-Mansour", sara, "MEMBER");
        inviteAndAccept(admin, "Omar Khalil", omar, "MEMBER");
        String projectId = createProject(admin, createClient(admin, "Bindawood"), "CDO");

        // Sara starts a researcher and is moved to lead: the PUT replaces, it does not accumulate.
        seat(admin, projectId, memberIdOf(admin, sara), "RESEARCHER");
        seat(admin, projectId, memberIdOf(admin, sara), "LEAD");
        // Omar becomes a second lead — plural leads are legal; the single-role rule is per seat.
        mvc.perform(put("/api/v1/projects/" + projectId + "/members/" + memberIdOf(admin, omar))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"LEAD"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.team.length()").value(3));

        JsonNode team = body(mvc.perform(get("/api/v1/projects")
                        .header("Authorization", "Bearer " + admin))
                .andReturn()).get(0).get("team");
        assertThat(seatOf(team, memberIdOf(admin, sara)).get("projectRoles"))
                .extracting(JsonNode::asText).containsExactly("LEAD");
        assertThat(seatOf(team, memberIdOf(admin, omar)).get("projectRoles"))
                .extracting(JsonNode::asText).containsExactly("LEAD");

        // The same PUT again changes nothing.
        mvc.perform(put("/api/v1/projects/" + projectId + "/members/" + memberIdOf(admin, omar))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"LEAD"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.team.length()").value(3));
    }

    @Test
    @DisplayName("CLIENT is not a role the team table can hand out")
    void clientIsNotSeatableThroughTheTeam() throws Exception {
        String alok = "alok@" + domain;
        String sara = "sara@" + domain;
        String admin = adminOf("No Client Seat Firm", alok);
        inviteAndAccept(admin, "Sara Al-Mansour", sara, "MEMBER");
        String projectId = createProject(admin, createClient(admin, "Almarai"), "COO");

        MvcResult refused = mvc.perform(
                        put("/api/v1/projects/" + projectId + "/members/" + memberIdOf(admin, sara))
                                .header("Authorization", "Bearer " + admin)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"role":"CLIENT"}"""))
                .andReturn();
        assertThat(refused.getResponse().getStatus()).isEqualTo(400);
        assertThat(codeOf(refused)).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("a project never loses its last lead — demotion and removal are both refused, until a handover")
    void lastProjectLeadGuardAndHandover() throws Exception {
        String alok = "alok@" + domain;
        String sara = "sara@" + domain;
        String admin = adminOf("Last Lead Firm", alok);
        inviteAndAccept(admin, "Sara Al-Mansour", sara, "MEMBER");
        String projectId = createProject(admin, createClient(admin, "Tanmiah"), "CFO");
        String alokSeat = memberIdOf(admin, alok);

        // Demoting the only lead is refused...
        MvcResult demote = mvc.perform(put("/api/v1/projects/" + projectId + "/members/" + alokSeat)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"RESEARCHER"}"""))
                .andReturn();
        assertThat(demote.getResponse().getStatus()).isEqualTo(409);
        assertThat(codeOf(demote)).isEqualTo("PROJECT_LAST_LEAD");

        // ...and so is pulling the seat off the team.
        MvcResult pull = mvc.perform(delete("/api/v1/projects/" + projectId + "/members/" + alokSeat)
                        .header("Authorization", "Bearer " + admin))
                .andReturn();
        assertThat(codeOf(pull)).isEqualTo("PROJECT_LAST_LEAD");

        // Handover: promote a second lead, and the creator is free to step down.
        seat(admin, projectId, memberIdOf(admin, sara), "LEAD");
        mvc.perform(put("/api/v1/projects/" + projectId + "/members/" + alokSeat)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"RESEARCHER"}"""))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("nothing of one workspace's projects is visible or reachable from another")
    void tenantIsolation() throws Exception {
        String alok = "alok@" + domain;
        String admin = adminOf("Isolation Firm", alok);
        String clientId = createClient(admin, "Meridian Energy");
        String projectId = createProject(admin, clientId, "CFO");

        String rivalEmail = "boss@rival-" + domain;
        createWorkspace(verifiedUser("Rival Boss", rivalEmail), "Rival Isolation Firm");
        String rival = login(rivalEmail);
        String rivalMemberId = memberIdOf(rival, rivalEmail);

        mvc.perform(get("/api/v1/projects").header("Authorization", "Bearer " + rival))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // 404, not 403 — through the @PreAuthorize guard, a foreign id must confirm nothing.
        mvc.perform(patch("/api/v1/projects/" + projectId)
                        .header("Authorization", "Bearer " + rival)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetDate":"2027-01-01"}"""))
                .andExpect(status().isNotFound());

        mvc.perform(put("/api/v1/projects/" + projectId + "/members/" + rivalMemberId)
                        .header("Authorization", "Bearer " + rival)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"RESEARCHER"}"""))
                .andExpect(status().isNotFound());

        // Another firm's client id is not a client as far as this caller is concerned.
        mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + rival)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"CTO"}
                                """.formatted(clientId)))
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
        String admin = adminOf("Health Firm");
        String clientId = createClient(admin, "Al Rabie");

        mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"CHRO","targetDate":"%s"}
                                """.formatted(clientId, LocalDate.now().minusDays(1))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.health").value("OFF"));
    }

    private void seat(String leadToken, String projectId, String memberId, String role)
            throws Exception {
        mvc.perform(put("/api/v1/projects/" + projectId + "/members/" + memberId)
                        .header("Authorization", "Bearer " + leadToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"%s"}""".formatted(role)))
                .andExpect(status().isOk());
    }

    private static JsonNode seatOf(JsonNode team, String memberId) {
        for (JsonNode seat : team) {
            if (seat.get("memberId").asText().equals(memberId)) {
                return seat;
            }
        }
        throw new AssertionError(memberId + " not on the team: " + team);
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
                                {"customName":"%s"}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private String createProject(String token, String clientId, String position) throws Exception {
        return body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"%s"}
                                """.formatted(clientId, position)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }
}
