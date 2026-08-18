package app.lightmove.api.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingEmailSender;
import app.lightmove.api.project.constant.ClientRepStatus;
import app.lightmove.api.project.model.ClientRepresentative;
import app.lightmove.api.project.repository.ClientRepresentativeRepository;
import app.lightmove.api.project.repository.PendingRepresentativeAttachmentRepository;
import app.lightmove.api.workspace.constant.InvitationStatus;
import app.lightmove.api.workspace.constant.MemberStatus;
import app.lightmove.api.workspace.repository.InvitationRepository;
import app.lightmove.api.workspace.repository.WorkspaceMemberRepository;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

/**
 * Withdrawing a client representative's access, from the registry tier. A revoke has to close all four
 * doors at once — the emailed token, the parked attach intent, the CLIENT project seat, and the workspace
 * membership the grant created — because any one left open is a person who still reads a mandate. What it
 * must <i>not</i> touch: a representative's staff role, or another client they also represent.
 */
@IntegrationTest
@Import(RecordingEmailSender.Config.class)
class ClientRepresentativeRevocationIntegrationTest extends FlowTestSupport {

    @Autowired ClientRepresentativeRepository representatives;
    @Autowired PendingRepresentativeAttachmentRepository pendingAttachments;
    @Autowired InvitationRepository invitations;
    @Autowired WorkspaceMemberRepository members;

    @Test
    @DisplayName("cancelling an invite kills the emailed link and the seat it was lined up for")
    void revokingAnInvitedRepresentativeKillsTheLinkAndTheParkedSeat() throws Exception {
        String alok = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Cancel Firm");
        String admin = login(alok);

        String clientId = createCustomClient(admin, "Acme Corp");
        String projectId = createProject(admin, clientId, "CFO Search");
        String repEmail = "typo@acme-corp.example";
        String representativeId = inviteRepresentative(admin, clientId, "Mistyped Rep", repEmail)
                .get("id").asText();
        attachRepresentative(admin, projectId, representativeId);
        String deadToken = email.latestTokenFor(repEmail);

        revoke(admin, clientId, representativeId).andExpect(status().isNoContent());

        ClientRepresentative revoked = representatives.findById(UUID.fromString(representativeId))
                .orElseThrow();
        assertThat(revoked.getStatus()).isEqualTo(ClientRepStatus.REVOKED);
        assertThat(invitations.findById(revoked.getInvitationId()).orElseThrow().getStatus())
                .isEqualTo(InvitationStatus.REVOKED);
        assertThat(pendingAttachments.findByRepresentativeId(UUID.fromString(representativeId))).isEmpty();

        // The link in their inbox is spent, so the address can no longer let itself in.
        MvcResult refused = acceptAttempt(deadToken, "Mistyped Rep");
        assertThat(refused.getResponse().getStatus()).isEqualTo(400);
        assertThat(codeOf(refused)).isEqualTo("INVITATION_INVALID");

        // Gone from both surfaces that counted them.
        mvc.perform(get("/api/v1/clients/" + clientId).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.representatives.length()").value(0));
        mvc.perform(get("/api/v1/projects").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].representatives.length()").value(0));

        // Idempotent: a second click is a race, not a mistake.
        revoke(admin, clientId, representativeId).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("revoking a live representative drops their seat, their membership and their reach")
    void revokingAnActiveRepresentativeEndsEverything() throws Exception {
        String alok = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Live Firm");
        String admin = login(alok);

        String clientId = createCustomClient(admin, "Beta Client");
        String projectId = createProject(admin, clientId, "CTO Search");
        String repEmail = "chair@beta-client.example";
        String representativeId = inviteRepresentative(admin, clientId, "Live Rep", repEmail)
                .get("id").asText();
        String rep = acceptAsNewUser(email.latestTokenFor(repEmail), "Live Rep");
        attachRepresentative(admin, projectId, representativeId);
        mvc.perform(get("/api/v1/projects/" + projectId + "/position")
                        .header("Authorization", "Bearer " + rep))
                .andExpect(status().isOk());

        UUID repUserId = representatives.findById(UUID.fromString(representativeId))
                .orElseThrow().getUserId();
        revoke(admin, clientId, representativeId).andExpect(status().isNoContent());

        // No client seat left on the mandate, so Team & access stops reporting them.
        JsonNode mandate = body(mvc.perform(get("/api/v1/projects")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn()).get(0);
        assertThat(mandate.get("representatives").size()).isEqualTo(0);
        assertThat(mandate.get("team").toString()).doesNotContain("CLIENT");

        // CLIENT was all they held, so the membership goes with it — an ACTIVE membership with no roles
        // would answer "not a pure client" and reach further than the portal guest it replaced.
        assertThat(members.findByUserIdAndStatus(repUserId, MemberStatus.ACTIVE)).isEmpty();

        // Their session outlives the revoke by design; the guard beans re-read the database, so it
        // reaches nothing. Masked as "no such workspace", not "forbidden".
        MvcResult afterRevoke = mvc.perform(get("/api/v1/projects").header("Authorization", "Bearer " + rep))
                .andReturn();
        assertThat(afterRevoke.getResponse().getStatus()).isEqualTo(404);
        assertThat(codeOf(afterRevoke)).isEqualTo("NOT_A_MEMBER");
    }

    @Test
    @DisplayName("a representative who also staffs the mandate keeps their staff seat and their membership")
    void revokingARepresentativeWhoIsAlsoStaffKeepsTheStaffSeat() throws Exception {
        String alok = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Both Hats Firm");
        String admin = login(alok);

        String colleague = "sam@" + domain;
        inviteAndAccept(admin, "Sam Staff", colleague, "MEMBER");
        String samMemberId = memberIdOf(admin, colleague);

        String clientId = createCustomClient(admin, "Gamma Client");
        String projectId = createProject(admin, clientId, "COO Search");
        String representativeId = inviteRepresentative(admin, clientId, "Sam Staff", colleague)
                .get("id").asText();
        attachRepresentative(admin, projectId, representativeId);
        mvc.perform(put("/api/v1/projects/" + projectId + "/members/" + samMemberId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"RESEARCHER"}"""))
                .andExpect(status().isOk());

        revoke(admin, clientId, representativeId).andExpect(status().isNoContent());

        // Only the CLIENT role comes off — the seat, the membership and the roster entry all stand.
        JsonNode seat = seatOf(body(mvc.perform(get("/api/v1/projects")
                        .header("Authorization", "Bearer " + admin))
                .andReturn()).get(0).get("team"), samMemberId);
        assertThat(seat.get("projectRoles")).extracting(JsonNode::asText).containsExactly("RESEARCHER");

        JsonNode roster = body(mvc.perform(get("/api/v1/members").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn());
        JsonNode sam = memberOf(roster, colleague);
        assertThat(sam.get("roles").toString()).contains("MEMBER").doesNotContain("CLIENT");

        // And they still work here: staff surfaces answer them as before.
        mvc.perform(get("/api/v1/clients").header("Authorization", "Bearer " + login(colleague)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("revoking one client's contact leaves the other client's portal open to the same person")
    void revokingOneClientLeavesTheOtherClientsPortalOpen() throws Exception {
        String alok = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Two Hats Firm");
        String admin = login(alok);

        String advisorEmail = "advisor@portfolio.example";
        String clientA = createCustomClient(admin, "Client A");
        String clientB = createCustomClient(admin, "Client B");
        String mandateOfB = createProject(admin, clientB, "CEO Search");

        String onA = inviteRepresentative(admin, clientA, "The Advisor", advisorEmail).get("id").asText();
        String rep = acceptAsNewUser(email.latestTokenFor(advisorEmail), "The Advisor");
        // The second client finds them already a member, so this row is ACTIVE from birth.
        String onB = inviteRepresentative(admin, clientB, "The Advisor", advisorEmail).get("id").asText();
        attachRepresentative(admin, mandateOfB, onB);

        revoke(admin, clientA, onA).andExpect(status().isNoContent());

        assertThat(representatives.findById(UUID.fromString(onA)).orElseThrow().getStatus())
                .isEqualTo(ClientRepStatus.REVOKED);
        // Their CLIENT grant stands behind two rows, so client B's mandate is still theirs to read —
        // which also proves the membership and its CLIENT role survived.
        mvc.perform(get("/api/v1/projects").header("Authorization", "Bearer " + rep))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(mandateOfB));
    }

    @Test
    @DisplayName("re-inviting a revoked address reuses the row and gives them a working link again")
    void reinvitingARevokedAddressReusesTheRow() throws Exception {
        String alok = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Second Chance Firm");
        String admin = login(alok);

        String clientId = createCustomClient(admin, "Delta Client");
        String repEmail = "again@delta-client.example";
        String representativeId = inviteRepresentative(admin, clientId, "Second Chance", repEmail)
                .get("id").asText();
        revoke(admin, clientId, representativeId).andExpect(status().isNoContent());

        JsonNode reinvited = inviteRepresentative(admin, clientId, "Second Chance", repEmail);
        assertThat(reinvited.get("id").asText()).isEqualTo(representativeId);
        assertThat(reinvited.get("status").asText()).isEqualTo("INVITED");

        String rep = acceptAsNewUser(email.latestTokenFor(repEmail), "Second Chance");
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + rep))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/clients/" + clientId).header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.representatives.length()").value(1))
                .andExpect(jsonPath("$.representatives[0].status").value("ACTIVE"));
    }

    @Test
    @DisplayName("a resend rotates the token, so the link in the first email dies")
    void resendingRotatesTheToken() throws Exception {
        String alok = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Resend Firm");
        String admin = login(alok);

        String clientId = createCustomClient(admin, "Epsilon Client");
        String repEmail = "waiting@epsilon-client.example";
        String representativeId = inviteRepresentative(admin, clientId, "Waiting Rep", repEmail)
                .get("id").asText();
        String firstToken = email.latestTokenFor(repEmail);

        mvc.perform(post("/api/v1/clients/" + clientId + "/representatives/" + representativeId + "/resend")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());

        String secondToken = email.latestTokenFor(repEmail);
        assertThat(secondToken).isNotEqualTo(firstToken);
        assertThat(acceptAttempt(firstToken, "Waiting Rep").getResponse().getStatus()).isEqualTo(400);
        acceptAsNewUser(secondToken, "Waiting Rep");

        // Nothing to re-send once they are in.
        MvcResult tooLate = mvc.perform(post("/api/v1/clients/" + clientId + "/representatives/"
                        + representativeId + "/resend")
                        .header("Authorization", "Bearer " + admin))
                .andReturn();
        assertThat(tooLate.getResponse().getStatus()).isEqualTo(400);
        assertThat(codeOf(tooLate)).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("a rival workspace cannot see the representative, and a portal guest cannot revoke one")
    void revokeIsScopedToTheWorkspaceAndToStaff() throws Exception {
        String alok = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Owner Firm");
        String admin = login(alok);

        String clientId = createCustomClient(admin, "Zeta Client");
        String repEmail = "guest@zeta-client.example";
        String representativeId = inviteRepresentative(admin, clientId, "Portal Guest", repEmail)
                .get("id").asText();
        String guest = acceptAsNewUser(email.latestTokenFor(repEmail), "Portal Guest");

        String rivalEmail = "boss@rival-" + domain;
        createWorkspace(verifiedUser("Rival Boss", rivalEmail), "Rival Firm");
        revoke(login(rivalEmail), clientId, representativeId).andExpect(status().isNotFound());

        // A pure client holds no CLIENT_RECORD_MANAGE, so they cannot revoke anyone — least of all
        // whoever else represents their own client.
        revoke(guest, clientId, representativeId).andExpect(status().isForbidden());

        // A representative of another client of the same workspace is a miss, not a hint.
        String otherClientId = createCustomClient(admin, "Eta Client");
        mvc.perform(delete("/api/v1/clients/" + otherClientId + "/representatives/" + representativeId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound());
        assertThat(representatives.findById(UUID.fromString(representativeId)).orElseThrow().getStatus())
                .isEqualTo(ClientRepStatus.ACTIVE);
    }

    private ResultActions revoke(String token, String clientId, String representativeId)
            throws Exception {
        return mvc.perform(delete("/api/v1/clients/" + clientId + "/representatives/" + representativeId)
                .header("Authorization", "Bearer " + token));
    }

    private static JsonNode seatOf(JsonNode team, String memberId) {
        for (JsonNode seat : team) {
            if (seat.get("memberId").asText().equals(memberId)) {
                return seat;
            }
        }
        throw new AssertionError(memberId + " not on the team: " + team);
    }

    private static JsonNode memberOf(JsonNode roster, String memberEmail) {
        for (JsonNode member : roster) {
            if (member.get("email").asText().equals(memberEmail)) {
                return member;
            }
        }
        throw new AssertionError(memberEmail + " not on the roster: " + roster);
    }

    private String createCustomClient(String adminToken, String name) throws Exception {
        return body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"%s"}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private String createProject(String adminToken, String clientId, String positionTitle) throws Exception {
        return body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"%s"}
                                """.formatted(clientId, positionTitle)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private JsonNode inviteRepresentative(String adminToken, String clientId, String fullName,
                                          String repEmail) throws Exception {
        return body(mvc.perform(post("/api/v1/clients/" + clientId + "/representatives")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"%s","position":"Chair","email":"%s"}
                                """.formatted(fullName, repEmail)))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private void attachRepresentative(String adminToken, String projectId, String representativeId)
            throws Exception {
        mvc.perform(post("/api/v1/projects/" + projectId + "/representatives")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"representativeId":"%s"}
                                """.formatted(representativeId)))
                .andExpect(status().isOk());
    }

    private MvcResult acceptAttempt(String token, String fullName) throws Exception {
        return mvc.perform(post("/api/v1/onboarding/accept-invitation-signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","fullName":"%s","password":"%s"}
                                """.formatted(token, fullName, PASSWORD)))
                .andReturn();
    }

    private String acceptAsNewUser(String token, String fullName) throws Exception {
        MvcResult accepted = acceptAttempt(token, fullName);
        assertThat(accepted.getResponse().getStatus()).as("accept failed: %s",
                accepted.getResponse().getContentAsString()).isEqualTo(201);
        return body(accepted).get("accessToken").asText();
    }
}
