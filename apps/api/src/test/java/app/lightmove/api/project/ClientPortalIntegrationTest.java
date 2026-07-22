package app.lightmove.api.project;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingEmailSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;

/**
 * The client-representative round trip and the portal's tenant fence: an invited representative accepts
 * into a CLIENT membership, sees only their own client through {@code /portal/me}, and is refused every
 * staff endpoint — while staff, in turn, cannot read the portal and never see the representative on the
 * roster.
 */
@IntegrationTest
@Import(RecordingEmailSender.Config.class)
class ClientPortalIntegrationTest extends FlowTestSupport {

    @Test
    @DisplayName("a representative accepts, sees only their client in the portal, and is fenced out of staff")
    void representativeAcceptsAndIsFenced() throws Exception {
        String alok = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Portal Firm");
        String admin = login(alok);

        String clientId = createCustomClient(admin, "Acme Corp");
        String repEmail = "khalid@acme-portal.example";
        inviteRepresentative(admin, clientId, "Khalid Al-Otaibi", "Group CHRO", repEmail);

        // The representative sets a password on the emailed link and lands in a session.
        String repToken = acceptAsNewUser(email.latestTokenFor(repEmail), "Khalid Al-Otaibi");

        // The portal shows exactly their client, and nothing wider.
        mvc.perform(get("/api/v1/portal/me").header("Authorization", "Bearer " + repToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Acme Corp"))
                .andExpect(jsonPath("$.mandates.length()").value(0));

        // Every staff endpoint is closed to them — action gate, then the staff-only roster gate.
        mvc.perform(get("/api/v1/clients").header("Authorization", "Bearer " + repToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/projects").header("Authorization", "Bearer " + repToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/members").header("Authorization", "Bearer " + repToken))
                .andExpect(status().isForbidden());

        // Staff cannot read the portal, and the representative is not on the staff roster.
        mvc.perform(get("/api/v1/portal/me").header("Authorization", "Bearer " + admin))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/members").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].email").value(alok));

        // Acceptance flipped the representative ACTIVE — the client's viewer summary reflects it.
        mvc.perform(get("/api/v1/clients").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].viewers.active").value(1))
                .andExpect(jsonPath("$[0].type").value("PROSPECT"));
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

    private void inviteRepresentative(String adminToken, String clientId, String fullName,
                                      String position, String repEmail) throws Exception {
        mvc.perform(post("/api/v1/clients/" + clientId + "/representatives")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"%s","position":"%s","email":"%s"}
                                """.formatted(fullName, position, repEmail)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("INVITED"));
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
