package app.lightmove.api.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingEmailSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * {@code ClientsController} gates every endpoint on the single workspace-tier {@code CLIENT_RECORD_MANAGE}
 * permission — ADMIN and MEMBER alike, no finer split, no project-tier concept at all. The pure-client
 * portal guest's 403 on {@code GET /api/v1/clients} is already covered by
 * {@link ClientAccessIntegrationTest#pureClientSeesOnlyAttachedProjectsReadOnly} and isn't repeated here.
 */
@IntegrationTest
@Import(RecordingEmailSender.Config.class)
class ClientAuthorizationIntegrationTest extends FlowTestSupport {

    @Test
    @DisplayName("a workspace admin lists, creates, reads and updates the registry")
    void adminHasFullAccess() throws Exception {
        String alok = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Admin Access Firm");
        String admin = login(alok);

        exerciseFullAccess(admin);
    }

    @Test
    @DisplayName("a plain workspace member has the same registry access as an admin")
    void memberHasFullAccess() throws Exception {
        String alok = "alok@" + domain;
        String sara = "sara@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Member Access Firm");
        String admin = login(alok);
        inviteAndAccept(admin, "Sara Al-Mansour", sara, "MEMBER");

        exerciseFullAccess(login(sara));
    }

    @Test
    @DisplayName("an unverified user reaches no client data, valid token or not")
    void unverifiedUserIsBlocked() throws Exception {
        createWorkspace(verifiedUser("Alok Kumar", "alok@" + domain), "Verified Firm");

        String unverified = signup("Impostor", "impostor@" + domain).get("accessToken").asText();
        mvc.perform(get("/api/v1/clients").header("Authorization", "Bearer " + unverified))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a denial through @PreAuthorize is still an RFC 9457 problem with its own code")
    void denialKeepsItsProblemShape() throws Exception {
        String alok = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Problem Shape Firm");
        String admin = login(alok);

        // A pure client (a representative with no staff role) is a member of the workspace but holds
        // no CLIENT_RECORD_MANAGE grant — the in-workspace denial case.
        String clientId = body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"Meridian Energy"}"""))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
        String repEmail = "chair@meridian.example";
        mvc.perform(post("/api/v1/clients/" + clientId + "/representatives")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Ext Rep","position":"Chair","email":"%s"}
                                """.formatted(repEmail)))
                .andExpect(status().isCreated());
        String repToken = body(mvc.perform(post("/api/v1/onboarding/accept-invitation-signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","fullName":"Ext Rep","password":"%s"}
                                """.formatted(email.latestTokenFor(repEmail), PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn()).get("accessToken").asText();

        MvcResult denied = mvc.perform(get("/api/v1/clients").header("Authorization", "Bearer " + repToken))
                .andReturn();
        assertThat(denied.getResponse().getStatus()).isEqualTo(403);
        assertThat(codeOf(denied)).isEqualTo("FORBIDDEN");

        // A verified user with no workspace at all: masked as "no such membership", not "forbidden".
        String outsider = verifiedUser("Out Sider", "out@other-" + domain);
        MvcResult masked = mvc.perform(get("/api/v1/clients").header("Authorization", "Bearer " + outsider))
                .andReturn();
        assertThat(masked.getResponse().getStatus()).isEqualTo(404);
        assertThat(codeOf(masked)).isEqualTo("NOT_A_MEMBER");
    }

    private void exerciseFullAccess(String token) throws Exception {
        String clientId = body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"Meridian Energy"}"""))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();

        mvc.perform(get("/api/v1/clients").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/clients/" + clientId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/v1/clients/" + clientId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Meridian Renewables"}"""))
                .andExpect(status().isOk());
    }
}
