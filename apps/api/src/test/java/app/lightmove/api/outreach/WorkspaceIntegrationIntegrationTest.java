package app.lightmove.api.outreach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.core.crypto.service.SecretCipher;
import app.lightmove.api.outreach.constant.CredentialMode;
import app.lightmove.api.outreach.constant.IntegrationProvider;
import app.lightmove.api.outreach.model.ProviderCredentials;
import app.lightmove.api.outreach.model.WorkspaceMailIntegration;
import app.lightmove.api.outreach.service.ProviderCredentialsResolver;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** Settings → Integrations: which OAuth app a workspace connects each provider through, kept by its admin. */
@IntegrationTest
class WorkspaceIntegrationIntegrationTest extends FlowTestSupport {

    private static final String SECRET = "contoso~very-secret-value";

    @Autowired JdbcTemplate jdbc;
    @Autowired SecretCipher cipher;
    @Autowired ProviderCredentialsResolver resolver;

    @Test
    @DisplayName("every provider starts on the shared app, offered where this deployment has one")
    void everyProviderStartsShared() throws Exception {
        String admin = adminOfNewWorkspace("Shared Start Firm");

        mvc.perform(get("/api/v1/workspace/integrations").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownAppsOffered").value(true))
                .andExpect(jsonPath("$.recallOffered").value(false))
                .andExpect(jsonPath("$.providers.length()").value(3))
                .andExpect(jsonPath("$.providers[0].provider").value("GOOGLE"))
                .andExpect(jsonPath("$.providers[0].mode").value("SHARED"))
                .andExpect(jsonPath("$.providers[0].sharedOffered").value(true))
                .andExpect(jsonPath("$.providers[0].redirectUri").value(
                        "http://localhost:5173/api/v1/outreach/mailbox/callback"))
                .andExpect(jsonPath("$.providers[1].provider").value("MICROSOFT"))
                .andExpect(jsonPath("$.providers[1].adminConsentUrl").value(startsWith(
                        "https://login.microsoftonline.com/organizations/adminconsent?client_id=uncava-microsoft-client")))
                .andExpect(jsonPath("$.providers[2].provider").value("ZOOM"))
                .andExpect(jsonPath("$.providers[2].sharedOffered").value(false))
                .andExpect(jsonPath("$.providers[2].adminConsentUrl").doesNotExist())
                .andExpect(jsonPath("$.providers[2].redirectUri").value(
                        "http://localhost:5173/api/v1/outreach/zoom/callback"));
    }

    @Test
    @DisplayName("an own app's secret is stored encrypted, decrypts back, and no read ever returns it")
    void ownSecretIsStoredEncryptedAndNeverReturned() throws Exception {
        String admin = adminOfNewWorkspace("Own Keys Firm");
        String workspaceId = workspaceOf(admin);

        MvcResult saved = mvc.perform(put("/api/v1/workspace/integrations/MICROSOFT")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mode":"OWN","clientId":"contoso-app","clientSecret":"%s",
                                 "tenantId":"contoso.onmicrosoft.com","secretExpiresOn":"2027-10-01"}
                                """.formatted(SECRET)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providers[1].mode").value("OWN"))
                .andExpect(jsonPath("$.providers[1].clientId").value("contoso-app"))
                .andExpect(jsonPath("$.providers[1].tenantId").value("contoso.onmicrosoft.com"))
                .andExpect(jsonPath("$.providers[1].secretSet").value(true))
                .andExpect(jsonPath("$.providers[1].secretExpiresOn").value("2027-10-01"))
                .andReturn();
        MvcResult read = mvc.perform(get("/api/v1/workspace/integrations").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(saved.getResponse().getContentAsString()).doesNotContain(SECRET).doesNotContain("clientSecret");
        assertThat(read.getResponse().getContentAsString()).doesNotContain(SECRET).doesNotContain("clientSecret");

        String stored = jdbc.queryForObject("""
                SELECT client_secret_encrypted FROM app_lm_workspace_mail_integration
                WHERE workspace_id = ?::uuid AND provider = 'MICROSOFT'
                """, String.class, workspaceId);
        assertThat(stored).isNotBlank().doesNotContain(SECRET);
        assertThat(cipher.decrypt(stored, WorkspaceMailIntegration.clientSecretContext(
                UUID.fromString(workspaceId), IntegrationProvider.MICROSOFT))).isEqualTo(SECRET);

        ProviderCredentials resolved =
                resolver.resolve(UUID.fromString(workspaceId), IntegrationProvider.MICROSOFT).orElseThrow();
        assertThat(resolved.mode()).isEqualTo(CredentialMode.OWN);
        assertThat(resolved.clientSecret()).isEqualTo(SECRET);

        assertThat(auditDetailsFor(workspaceId)).isNotEmpty().allSatisfy(details ->
                assertThat(details).doesNotContain(SECRET));
    }

    @Test
    @DisplayName("a blank secret keeps the one held for the same app, and a new client id needs a new one")
    void blankSecretKeepsTheStoredOne() throws Exception {
        String admin = adminOfNewWorkspace("Keep Secret Firm");
        saveOwnGoogleApp(admin, "acme-app", SECRET).andExpect(status().isOk());

        saveOwnGoogleApp(admin, "acme-app", "").andExpect(status().isOk())
                .andExpect(jsonPath("$.providers[0].secretSet").value(true));
        assertThat(resolver.resolve(UUID.fromString(workspaceOf(admin)), IntegrationProvider.GOOGLE).orElseThrow()
                .clientSecret()).isEqualTo(SECRET);

        MvcResult refused = saveOwnGoogleApp(admin, "another-app", "").andReturn();
        assertThat(refused.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(refused).at("/fieldErrors/clientSecret").asText()).isNotBlank();
    }

    @Test
    @DisplayName("saving the same app again with nothing changed records nothing")
    void unchangedSaveRecordsNothing() throws Exception {
        String admin = adminOfNewWorkspace("Unchanged Firm");
        saveOwnGoogleApp(admin, "acme-app", SECRET).andExpect(status().isOk());

        saveOwnGoogleApp(admin, "acme-app", "").andExpect(status().isOk());

        assertThat(auditDetailsFor(workspaceOf(admin))).hasSize(1);
    }

    @Test
    @DisplayName("keys sent with the shared app are refused rather than dropped")
    void sharedWithKeysIsRefused() throws Exception {
        String admin = adminOfNewWorkspace("Shared Keys Firm");

        MvcResult refused = mvc.perform(put("/api/v1/workspace/integrations/GOOGLE")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mode":"SHARED","clientId":"acme-app","clientSecret":"%s"}""".formatted(SECRET)))
                .andReturn();

        assertThat(refused.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(refused).at("/fieldErrors/mode").asText()).isNotBlank();
    }

    @Test
    @DisplayName("a Microsoft app needs its tenant, and no other provider takes one")
    void tenantIsMicrosoftsAlone() throws Exception {
        String admin = adminOfNewWorkspace("Tenant Firm");

        MvcResult noTenant = mvc.perform(put("/api/v1/workspace/integrations/MICROSOFT")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mode":"OWN","clientId":"contoso-app","clientSecret":"%s"}""".formatted(SECRET)))
                .andReturn();
        assertThat(noTenant.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(noTenant).at("/fieldErrors/tenantId").asText()).isNotBlank();

        MvcResult zoomTenant = mvc.perform(put("/api/v1/workspace/integrations/ZOOM")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mode":"OWN","clientId":"zoom-app","clientSecret":"%s","tenantId":"contoso"}
                                """.formatted(SECRET)))
                .andReturn();
        assertThat(zoomTenant.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("returning to the shared app discards the workspace's keys, and is on the record")
    void returningToSharedDiscardsKeys() throws Exception {
        String admin = adminOfNewWorkspace("Return Firm");
        String workspaceId = workspaceOf(admin);
        saveOwnGoogleApp(admin, "acme-app", SECRET).andExpect(status().isOk());

        mvc.perform(delete("/api/v1/workspace/integrations/GOOGLE").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providers[0].mode").value("SHARED"))
                .andExpect(jsonPath("$.providers[0].clientId").doesNotExist())
                .andExpect(jsonPath("$.providers[0].secretSet").value(false));

        Integer heldSecrets = jdbc.queryForObject("""
                SELECT count(*) FROM app_lm_workspace_mail_integration
                WHERE workspace_id = ?::uuid AND client_secret_encrypted IS NOT NULL
                """, Integer.class, workspaceId);
        assertThat(heldSecrets).isZero();
        assertThat(resolver.resolve(UUID.fromString(workspaceId), IntegrationProvider.GOOGLE).orElseThrow()
                .clientId()).isEqualTo("uncava-google-client");
        assertThat(auditDetailsFor(workspaceId))
                .anySatisfy(details -> assertThat(details).contains("\"mode\": \"OWN\""))
                .anySatisfy(details -> assertThat(details).contains("\"mode\": \"SHARED\""));
    }

    @Test
    @DisplayName("members and client representatives can neither read nor change the integrations")
    void refusedForMemberAndClient() throws Exception {
        String alok = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Guarded Integrations Firm");
        String admin = login(alok);
        inviteAndAccept(admin, "Sara Al-Mansour", "sara@" + domain, "MEMBER");
        String member = login("sara@" + domain);
        String client = clientRepresentative(admin, "Rana Client", "rana@client-" + domain);

        for (String refusedCaller : new String[] {member, client}) {
            mvc.perform(get("/api/v1/workspace/integrations").header("Authorization", "Bearer " + refusedCaller))
                    .andExpect(status().isForbidden());
            saveOwnGoogleApp(refusedCaller, "acme-app", SECRET).andExpect(status().isForbidden());
            mvc.perform(delete("/api/v1/workspace/integrations/GOOGLE")
                            .header("Authorization", "Bearer " + refusedCaller))
                    .andExpect(status().isForbidden());
        }
    }

    private ResultActions saveOwnGoogleApp(String bearerToken, String clientId, String clientSecret)
            throws Exception {
        return mvc.perform(put("/api/v1/workspace/integrations/GOOGLE")
                .header("Authorization", "Bearer " + bearerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"mode":"OWN","clientId":"%s","clientSecret":"%s"}""".formatted(clientId, clientSecret)));
    }

    private String adminOfNewWorkspace(String name) throws Exception {
        String alok = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), name);
        return login(alok);
    }

    private String workspaceOf(String bearerToken) throws Exception {
        return body(mvc.perform(get("/api/v1/workspace").header("Authorization", "Bearer " + bearerToken))
                .andReturn()).get("id").asText();
    }

    private List<String> auditDetailsFor(String workspaceId) {
        return jdbc.queryForList("""
                SELECT metadata::text FROM app_lm_audit_event
                WHERE event_type = 'WORKSPACE_UPDATED' AND workspace_id = ?::uuid
                  AND metadata ->> 'section' = 'integrations'
                """, String.class, workspaceId);
    }
}
