package app.lightmove.api.project;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * {@code ClientsController} gates every endpoint on the single workspace-tier {@code CLIENT_RECORD_MANAGE}
 * permission — ADMIN and MEMBER alike, no finer split, no project-tier concept at all. The pure-client
 * portal guest's 403 on {@code GET /api/v1/clients} is already covered by
 * {@link ClientAccessIntegrationTest#pureClientSeesOnlyAttachedProjectsReadOnly}, the verified-email
 * gate by {@link app.lightmove.api.auth.AuthFlowIntegrationTest}, and the problem shape of a
 * {@code @PreAuthorize} denial by {@link ProjectAuthorizationIntegrationTest#denialKeepsItsProblemShape};
 * none of them is repeated here.
 */
@IntegrationTest
class ClientAuthorizationIntegrationTest extends FlowTestSupport {

    @Test
    @DisplayName("an admin and a plain member alike list, create, read and update the registry")
    void everyStaffRoleHasFullAccess() throws Exception {
        String alok = "alok@" + domain;
        String sara = "sara@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Registry Access Firm");
        String admin = login(alok);
        inviteAndAccept(admin, "Sara Al-Mansour", sara, "MEMBER");

        exerciseFullAccess(admin, "Meridian Energy");
        exerciseFullAccess(login(sara), "Gulf Retail Holding");
    }

    private void exerciseFullAccess(String token, String clientName) throws Exception {
        String clientId = body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"%s"}""".formatted(clientName)))
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
                                {"name":"%s Renewables"}""".formatted(clientName)))
                .andExpect(status().isOk());
    }
}
