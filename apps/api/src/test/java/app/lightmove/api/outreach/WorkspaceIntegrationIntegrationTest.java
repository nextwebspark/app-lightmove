package app.lightmove.api.outreach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingEmailSender;
import app.lightmove.api.core.crypto.service.SecretCipher;
import app.lightmove.api.outreach.constant.CredentialMode;
import app.lightmove.api.outreach.constant.IntegrationProvider;
import app.lightmove.api.outreach.model.ProviderCredentials;
import app.lightmove.api.outreach.model.WorkspaceMailIntegration;
import app.lightmove.api.outreach.repository.WorkspaceMailIntegrationRepository;
import app.lightmove.api.outreach.service.IntegrationSecretExpiryWarnings;
import app.lightmove.api.outreach.service.ProviderCredentialsResolver;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.util.UriComponentsBuilder;

/** Settings → Integrations: which OAuth app a workspace connects each provider through, kept by its admin. */
@IntegrationTest
class WorkspaceIntegrationIntegrationTest extends FlowTestSupport {

    private static final String SECRET = "contoso~very-secret-value";
    private static final String MICROSOFT_APP = "6a1c3e5f-7b9d-4f2a-8c4e-0d2b4f6a8c0e";
    private static final String GOOGLE_APP = "acme-app.apps.googleusercontent.com";
    private static final DateTimeFormatter EMAIL_DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    @Autowired JdbcTemplate jdbc;
    @Autowired SecretCipher cipher;
    @Autowired ProviderCredentialsResolver resolver;
    @Autowired IntegrationSecretExpiryWarnings expiryWarnings;
    @Autowired RecordingEmailSender emails;
    @Autowired WorkspaceMailIntegrationRepository integrations;

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

        String expiresOn = today().plusMonths(11).toString();
        MvcResult saved = mvc.perform(put("/api/v1/workspace/integrations/MICROSOFT")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mode":"OWN","clientId":"%s","clientSecret":"%s",
                                 "tenantId":"contoso.onmicrosoft.com","secretExpiresOn":"%s"}
                                """.formatted(MICROSOFT_APP, SECRET, expiresOn)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providers[1].mode").value("OWN"))
                .andExpect(jsonPath("$.providers[1].clientId").value(MICROSOFT_APP))
                .andExpect(jsonPath("$.providers[1].tenantId").value("contoso.onmicrosoft.com"))
                .andExpect(jsonPath("$.providers[1].secretSet").value(true))
                .andExpect(jsonPath("$.providers[1].secretExpiresOn").value(expiresOn))
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
        saveOwnGoogleApp(admin, GOOGLE_APP, SECRET).andExpect(status().isOk());

        saveOwnGoogleApp(admin, GOOGLE_APP, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.providers[0].secretSet").value(true));
        assertThat(resolver.resolve(UUID.fromString(workspaceOf(admin)), IntegrationProvider.GOOGLE).orElseThrow()
                .clientSecret()).isEqualTo(SECRET);

        MvcResult refused = saveOwnGoogleApp(admin, "another-app.apps.googleusercontent.com", "").andReturn();
        assertThat(refused.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(refused).at("/fieldErrors/clientSecret").asText()).isNotBlank();
    }

    @Test
    @DisplayName("saving the same app again with nothing changed records nothing")
    void unchangedSaveRecordsNothing() throws Exception {
        String admin = adminOfNewWorkspace("Unchanged Firm");
        saveOwnGoogleApp(admin, GOOGLE_APP, SECRET).andExpect(status().isOk());

        saveOwnGoogleApp(admin, GOOGLE_APP, "").andExpect(status().isOk());

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
                                {"mode":"OWN","clientId":"%s","clientSecret":"%s"}""".formatted(MICROSOFT_APP, SECRET)))
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
        saveOwnGoogleApp(admin, GOOGLE_APP, SECRET).andExpect(status().isOk());

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
    @DisplayName("Microsoft's admin-consent return is recorded for the shared app and audited, whatever the mode")
    void microsoftAdminConsentIsRecorded() throws Exception {
        String admin = adminOfNewWorkspace("Consenting Firm");
        String workspaceId = workspaceOf(admin);
        String tenant = "8f3a6c1e-2b4d-4e5f-9a0b-1c2d3e4f5a6b";
        String state = adminConsentStateOf(admin);

        recordAdminConsent(admin, tenant, "a-state-our-link-never-carried").andExpect(status().isBadRequest());
        recordAdminConsent(admin, "contoso.onmicrosoft.com", state).andExpect(status().isBadRequest());
        assertThat(auditDetailsFor(workspaceId)).isEmpty();

        recordAdminConsent(admin, tenant, state)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providers[1].provider").value("MICROSOFT"))
                .andExpect(jsonPath("$.providers[1].mode").value("SHARED"))
                .andExpect(jsonPath("$.providers[1].adminConsentedAt").isNotEmpty())
                .andExpect(jsonPath("$.providers[1].adminConsentTenantId").value(tenant))
                .andExpect(jsonPath("$.providers[0].adminConsentedAt").doesNotExist());
        assertThat(auditDetailsFor(workspaceId))
                .anySatisfy(details -> assertThat(details).contains("\"adminConsentTenant\": \"" + tenant + "\""));
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
            saveOwnGoogleApp(refusedCaller, GOOGLE_APP, SECRET).andExpect(status().isForbidden());
            mvc.perform(delete("/api/v1/workspace/integrations/GOOGLE")
                            .header("Authorization", "Bearer " + refusedCaller))
                    .andExpect(status().isForbidden());
            recordAdminConsent(refusedCaller, "8f3a6c1e-2b4d-4e5f-9a0b-1c2d3e4f5a6b", "any-state")
                    .andExpect(status().isForbidden());
        }
    }

    private ResultActions recordAdminConsent(String bearerToken, String tenantId, String state) throws Exception {
        return mvc.perform(post("/api/v1/workspace/integrations/MICROSOFT/admin-consent")
                .header("Authorization", "Bearer " + bearerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tenantId\":\"%s\",\"state\":\"%s\"}".formatted(tenantId, state)));
    }

    /** The state the workspace's own admin-consent link carries, as Microsoft would hand it back. */
    private String adminConsentStateOf(String bearerToken) throws Exception {
        String link = body(mvc.perform(get("/api/v1/workspace/integrations").header("Authorization", "Bearer " + bearerToken))
                .andReturn()).get("providers").get(1).get("adminConsentUrl").asText();
        return UriComponentsBuilder.fromUriString(link).build().getQueryParams().getFirst("state");
    }

    private ResultActions saveOwnGoogleApp(String bearerToken, String clientId, String clientSecret)
            throws Exception {
        return mvc.perform(put("/api/v1/workspace/integrations/GOOGLE")
                .header("Authorization", "Bearer " + bearerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"mode":"OWN","clientId":"%s","clientSecret":"%s"}""".formatted(clientId, clientSecret)));
    }

    @Test
    @DisplayName("an own app's admins are emailed 30 and 7 days before its secret expires and on the day, once each")
    void adminsAreWarnedAheadOfASecretExpiring() throws Exception {
        String admin = adminOfNewWorkspace("Expiring Secret Firm");
        String adminEmail = "alok@" + domain;
        String memberEmail = "sara@" + domain;
        inviteAndAccept(admin, "Sara Member", memberEmail, "MEMBER");
        LocalDate expiresOn = today().plusMonths(20);
        saveOwnMicrosoftApp(admin, expiresOn.toString());

        expiryWarnings.warnAt(expiresOn.minusDays(42));
        assertThat(expiryWarningsTo(adminEmail)).isEmpty();

        expiryWarnings.warnAt(expiresOn.minusDays(23));
        expiryWarnings.warnAt(expiresOn.minusDays(22));
        assertThat(expiryWarningsTo(adminEmail))
                .containsExactly("Your Microsoft 365 app's client secret expires on " + EMAIL_DATE.format(expiresOn));
        UUID integrationId = UUID.fromString(jdbc.queryForObject("SELECT id::text FROM app_lm_workspace_mail_integration "
                + "WHERE workspace_id = ?::uuid AND provider = 'MICROSOFT'", String.class, workspaceOf(admin)));
        assertThat(integrations.claimExpiryWarning(integrationId, expiresOn, 30))
                .as("a second instance finds the warning already claimed").isZero();

        expiryWarnings.warnAt(expiresOn.minusDays(5));
        expiryWarnings.warnAt(expiresOn.plusDays(1));
        assertThat(expiryWarningsTo(adminEmail)).hasSize(3)
                .last().isEqualTo("Your Microsoft 365 app's client secret has expired");
        assertThat(expiryWarningsTo(memberEmail)).as("only who manages the workspace").isEmpty();

        LocalDate renewedOn = today().plusMonths(22);
        saveOwnMicrosoftApp(admin, renewedOn.toString());
        expiryWarnings.warnAt(renewedOn.minusDays(23));
        assertThat(expiryWarningsTo(adminEmail)).as("a new expiry date warns afresh").hasSize(4);
    }

    private void saveOwnMicrosoftApp(String admin, String secretExpiresOn) throws Exception {
        mvc.perform(put("/api/v1/workspace/integrations/MICROSOFT")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mode":"OWN","clientId":"%s","clientSecret":"%s",
                                 "tenantId":"contoso.onmicrosoft.com","secretExpiresOn":"%s"}
                                """.formatted(MICROSOFT_APP, SECRET, secretExpiresOn)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("keys that are a paste of the wrong value are refused field by field, and nothing is saved")
    void wrongPastesAreRefused() throws Exception {
        String admin = adminOfNewWorkspace("Paste Check Firm");

        assertRefusedOn(saveOwnGoogleApp(admin, GOOGLE_APP, "GOCSPX-abc123\\n"), "clientSecret");
        assertRefusedOn(saveOwnGoogleApp(admin, GOOGLE_APP, " GOCSPX-abc123"), "clientSecret");
        assertRefusedOn(saveOwnGoogleApp(admin, "123456-abc", SECRET), "clientId");
        assertRefusedOn(saveOwn(admin, "GOOGLE", """
                {"mode":"OWN","clientId":"%s","clientSecret":"%s","secretExpiresOn":"%s"}
                """.formatted(GOOGLE_APP, SECRET, today().plusMonths(6))), "secretExpiresOn");

        assertRefusedOn(saveOwnMicrosoft(admin, "contoso-app", SECRET, "contoso.onmicrosoft.com", null), "clientId");
        assertRefusedOn(saveOwnMicrosoft(admin, MICROSOFT_APP, "8c1d2e3f-0a1b-4c2d-9e8f-7a6b5c4d3e2f",
                "contoso.onmicrosoft.com", null), "clientSecret");
        for (String tenant : new String[] {"common", "organizations", "consumers", ".."}) {
            assertRefusedOn(saveOwnMicrosoft(admin, MICROSOFT_APP, SECRET, tenant, null), "tenantId");
        }
        assertRefusedOn(saveOwnMicrosoft(admin, MICROSOFT_APP, SECRET, "contoso.onmicrosoft.com",
                today().minusDays(1).toString()), "secretExpiresOn");
        assertRefusedOn(saveOwnMicrosoft(admin, MICROSOFT_APP, SECRET, "contoso.onmicrosoft.com",
                today().plusMonths(24).plusDays(2).toString()), "secretExpiresOn");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM app_lm_workspace_mail_integration "
                + "WHERE workspace_id = ?::uuid AND mode = 'OWN'", Integer.class, workspaceOf(admin))).isZero();

        saveOwnMicrosoft(admin, MICROSOFT_APP, SECRET, "8f3a6c1e-2b4d-4e5f-9a0b-1c2d3e4f5a6b",
                today().plusMonths(24).toString()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("a lapsed expiry already on the record saves unchanged with a new tenant")
    void lapsedExpiryOnRecordStillSaves() throws Exception {
        String admin = adminOfNewWorkspace("Lapsed Expiry Firm");
        saveOwnMicrosoftApp(admin, today().plusMonths(3).toString());
        jdbc.update("UPDATE app_lm_workspace_mail_integration SET secret_expires_on = ? "
                + "WHERE workspace_id = ?::uuid AND provider = 'MICROSOFT'", today().minusDays(3),
                UUID.fromString(workspaceOf(admin)));

        saveOwnMicrosoft(admin, MICROSOFT_APP, "", "contoso.example.com", today().minusDays(3).toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providers[1].tenantId").value("contoso.example.com"));
    }

    private ResultActions saveOwnMicrosoft(String admin, String clientId, String secret, String tenantId,
                                           String secretExpiresOn) throws Exception {
        return saveOwn(admin, "MICROSOFT", """
                {"mode":"OWN","clientId":"%s","clientSecret":"%s","tenantId":"%s","secretExpiresOn":%s}
                """.formatted(clientId, secret, tenantId, secretExpiresOn == null ? "null" : "\"" + secretExpiresOn + "\""));
    }

    private ResultActions saveOwn(String admin, String provider, String json) throws Exception {
        return mvc.perform(put("/api/v1/workspace/integrations/" + provider)
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private void assertRefusedOn(ResultActions refusal, String field) throws Exception {
        MvcResult refused = refusal.andReturn();
        assertThat(refused.getResponse().getStatus()).as(field).isEqualTo(400);
        assertThat(body(refused).at("/fieldErrors/" + field).asText()).as(field).isNotBlank();
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    private List<String> expiryWarningsTo(String recipient) {
        return emails.subjectsFor(recipient).stream().filter(subject -> subject.contains("client secret")).toList();
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
