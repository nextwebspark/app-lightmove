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
import app.lightmove.api.core.email.model.EmailMessage;
import app.lightmove.api.project.model.ClientRepresentative;
import app.lightmove.api.project.repository.ClientRepresentativeRepository;
import app.lightmove.api.workspace.constant.InvitationStatus;
import app.lightmove.api.workspace.constant.MemberStatus;
import app.lightmove.api.workspace.model.Invitation;
import app.lightmove.api.workspace.repository.InvitationRepository;
import app.lightmove.api.workspace.repository.WorkspaceMemberRepository;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * The client-representative access model: multi-role membership, and the read-only, per-project scope of
 * a pure client. A representative who is already a member gains the CLIENT role without a fresh invite; a
 * stranger accepts one. A pure client sees only the mandates they are attached to, read-only, and no
 * staff surface — while a member who <i>also</i> represents a client stays fully staff.
 */
@IntegrationTest
@Import(RecordingEmailSender.Config.class)
class ClientAccessIntegrationTest extends FlowTestSupport {

    @Autowired InvitationRepository invitations;
    @Autowired ClientRepresentativeRepository representatives;
    @Autowired WorkspaceMemberRepository members;

    @Test
    @DisplayName("an existing member named a representative gains the CLIENT role with a notice, not a new invite")
    void existingMemberBecomesRepresentativeWithoutInvite() throws Exception {
        String alok = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Dual Firm");
        String admin = login(alok);

        String colleague = "sam@" + domain;
        inviteAndAccept(admin, "Sam Staff", colleague, "MEMBER");

        String clientId = createCustomClient(admin, "Acme Corp");
        JsonNode representative = inviteRepresentative(admin, clientId, "Sam Staff", "Sponsor", colleague);

        // No invitation: they were already in, so the representative is ACTIVE at once.
        assertThat(representative.get("status").asText()).isEqualTo("ACTIVE");

        // The most recent mail to them is an informational notice — no accept token to redeem.
        EmailMessage notice = email.sent().reversed().stream()
                .filter(message -> colleague.equalsIgnoreCase(message.to()))
                .findFirst().orElseThrow();
        assertThat(notice.subject()).contains("represent");
        assertThat(notice.textBody()).doesNotContain("token=");

        // Their membership now holds both roles, and they still appear among staff.
        JsonNode roster = body(mvc.perform(get("/api/v1/members").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn());
        JsonNode sam = null;
        for (JsonNode member : roster) {
            if (member.get("email").asText().equals(colleague)) {
                sam = member;
            }
        }
        assertThat(sam).as("Sam is on the staff roster").isNotNull();
        assertThat(sam.get("roles").toString()).contains("MEMBER").contains("CLIENT");

        // A member who also represents a client is still staff — the CLIENT role does not fence them.
        String samToken = login(colleague);
        mvc.perform(get("/api/v1/clients").header("Authorization", "Bearer " + samToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a pure client sees only the mandates attached to them, read-only, and no staff surface")
    void pureClientSeesOnlyAttachedProjectsReadOnly() throws Exception {
        String alok = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Access Firm");
        String admin = login(alok);

        String clientId = createCustomClient(admin, "Beta Client");
        String attached = createProject(admin, clientId, "CFO Search");
        String hidden = createProject(admin, clientId, "CTO Search");

        String repEmail = "ext@beta-client.example";
        JsonNode representative = inviteRepresentative(admin, clientId, "Ext Rep", "Chair", repEmail);
        String rep = acceptAsNewUser(email.latestTokenFor(repEmail), "Ext Rep");

        attachRepresentative(admin, attached, representative.get("id").asText());

        // The list is scoped to the one mandate they're attached to.
        mvc.perform(get("/api/v1/projects").header("Authorization", "Bearer " + rep))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(attached));

        // They may read the attached mandate's content...
        mvc.perform(get("/api/v1/projects/" + attached + "/position").header("Authorization", "Bearer " + rep))
                .andExpect(status().isOk());
        // ...but not a mandate they are not on...
        mvc.perform(get("/api/v1/projects/" + hidden + "/position").header("Authorization", "Bearer " + rep))
                .andExpect(status().isForbidden());
        // ...and cannot edit even the one they can see.
        mvc.perform(patch("/api/v1/projects/" + attached)
                        .header("Authorization", "Bearer " + rep)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetDate":"2027-01-01"}
                                """))
                .andExpect(status().isForbidden());

        // Every staff surface is closed to them.
        mvc.perform(get("/api/v1/clients").header("Authorization", "Bearer " + rep))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/members").header("Authorization", "Bearer " + rep))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/companies/sectors").header("Authorization", "Bearer " + rep))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("another firm's client id does not exist as far as a rival admin is concerned")
    void crossWorkspaceClientProbeIs404() throws Exception {
        String alok = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Owner Firm");
        String clientId = createCustomClient(login(alok), "Meridian Foods");

        String rivalEmail = "boss@rival-" + domain;
        createWorkspace(verifiedUser("Rival Boss", rivalEmail), "Rival Firm");
        String rival = login(rivalEmail);

        mvc.perform(get("/api/v1/clients/" + clientId).header("Authorization", "Bearer " + rival))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a staff invite to a rep's email is a separate MEMBER invitation, and the client invite stays off staff surfaces")
    void staffInviteDoesNotCollideWithClientInvite() throws Exception {
        String alok = "alok@" + domain;
        UUID workspaceId = UUID.fromString(createWorkspace(verifiedUser("Alok Kumar", alok), "Split Firm"));
        String admin = login(alok);

        String clientId = createCustomClient(admin, "Acme Corp");
        String shared = "sam@contractor-portal.example";
        inviteRepresentative(admin, clientId, "Sam Rep", "Advisor", shared);

        // The client-rep invitation never shows on the staff Members screen.
        mvc.perform(get("/api/v1/invitations").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // Inviting the same address as staff mints a NEW MEMBER invitation — it does not refresh the client one.
        mvc.perform(post("/api/v1/invitations")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"email":"%s","role":"MEMBER"}]
                                """.formatted(shared)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/invitations").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].email").value(shared))
                .andExpect(jsonPath("$[0].role").value("MEMBER"));

        // Two distinct pending rows coexist: one staff (client_id null), one client.
        assertThat(invitations.findByWorkspaceIdAndStatus(workspaceId, InvitationStatus.PENDING)).hasSize(2);
        assertThat(invitations.findByWorkspaceIdAndClientIdIsNullAndStatus(workspaceId, InvitationStatus.PENDING))
                .hasSize(1);

        // The client invitation's id is invisible to the staff revoke/resend endpoints.
        Invitation clientInvite = invitations.findByWorkspaceIdAndClientIdAndEmailAndStatus(
                        workspaceId, UUID.fromString(clientId), shared, InvitationStatus.PENDING)
                .orElseThrow();
        mvc.perform(delete("/api/v1/invitations/" + clientInvite.getId())
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/invitations/" + clientInvite.getId() + "/resend")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a pure client cannot be seated on a project team through the staff path")
    void clientRepresentativeCannotBeSeatedByStaffPath() throws Exception {
        String alok = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Seat Firm");
        String admin = login(alok);

        String clientId = createCustomClient(admin, "Acme Corp");
        String repEmail = "rep@acme-seat.example";
        inviteRepresentative(admin, clientId, "Rep Person", "Advisor", repEmail);
        acceptAsNewUser(email.latestTokenFor(repEmail), "Rep Person");

        ClientRepresentative rep = representatives
                .findByClientIdAndEmailIgnoreCase(UUID.fromString(clientId), repEmail).orElseThrow();
        UUID repMemberId = members.findByUserIdAndStatus(rep.getUserId(), MemberStatus.ACTIVE)
                .orElseThrow().getId();

        String projectId = createProject(admin, clientId, "Chief Financial Officer");

        mvc.perform(put("/api/v1/projects/" + projectId + "/members/" + repMemberId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roles":["RESEARCHER"]}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a representative of one client cannot be attached to another client's mandate")
    void representativeOfAnotherClientCannotBeAttached() throws Exception {
        String alok = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Boundary Firm");
        String admin = login(alok);

        String clientA = createCustomClient(admin, "Client A");
        String clientB = createCustomClient(admin, "Client B");
        String mandateOfB = createProject(admin, clientB, "CEO Search");

        String repEmail = "rep@client-a.example";
        JsonNode representative = inviteRepresentative(admin, clientA, "Rep A", "Chair", repEmail);
        String rep = acceptAsNewUser(email.latestTokenFor(repEmail), "Rep A");

        MvcResult crossAttach = mvc.perform(post("/api/v1/projects/" + mandateOfB + "/representatives")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"representativeId":"%s"}
                                """.formatted(representative.get("id").asText())))
                .andReturn();
        assertThat(crossAttach.getResponse().getStatus()).isEqualTo(400);
        assertThat(codeOf(crossAttach)).isEqualTo("VALIDATION_FAILED");

        // The other client's mandate never appears in the representative's scoped list.
        mvc.perform(get("/api/v1/projects").header("Authorization", "Bearer " + rep))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("re-inviting an ACTIVE representative is refused before any email goes out")
    void duplicateInviteOfActiveRepresentativeSendsNoEmail() throws Exception {
        String alok = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Dupe Firm");
        String admin = login(alok);

        String colleague = "sam@" + domain;
        inviteAndAccept(admin, "Sam Staff", colleague, "MEMBER");

        String clientId = createCustomClient(admin, "Acme Corp");
        inviteRepresentative(admin, clientId, "Sam Staff", "Sponsor", colleague);

        email.clear();
        MvcResult duplicate = mvc.perform(post("/api/v1/clients/" + clientId + "/representatives")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Sam Staff","position":"Sponsor","email":"%s"}
                                """.formatted(colleague)))
                .andReturn();
        assertThat(duplicate.getResponse().getStatus()).isEqualTo(400);
        assertThat(codeOf(duplicate)).isEqualTo("VALIDATION_FAILED");
        assertThat(email.sent()).isEmpty();
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

    private JsonNode inviteRepresentative(String adminToken, String clientId, String fullName,
                                          String position, String repEmail) throws Exception {
        return body(mvc.perform(post("/api/v1/clients/" + clientId + "/representatives")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"%s","position":"%s","email":"%s"}
                                """.formatted(fullName, position, repEmail)))
                .andExpect(status().isCreated())
                .andReturn());
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

    private String acceptAsNewUser(String token, String fullName) throws Exception {
        return body(mvc.perform(post("/api/v1/onboarding/accept-invitation-signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","fullName":"%s","password":"%s"}
                                """.formatted(token, fullName, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn()).get("accessToken").asText();
    }
}
