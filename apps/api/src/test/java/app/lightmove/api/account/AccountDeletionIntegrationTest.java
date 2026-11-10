package app.lightmove.api.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FixtureJdbc;
import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingMailboxGateway;
import app.lightmove.api.core.security.mfa.TestTotp;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/** #926: a person deletes their own account, or downloads it, from Settings → Profile. */
@IntegrationTest
class AccountDeletionIntegrationTest extends FlowTestSupport {

    @Autowired @FixtureJdbc JdbcTemplate db;
    @Autowired RecordingMailboxGateway mailboxes;

    @BeforeEach
    void resetDoubles() {
        mailboxes.clear();
    }

    @Test
    @DisplayName("a member of two workspaces leaves both, loses every way in, and stays behind as a former member")
    void deletionLeavesEveryWorkspaceAndAnonymisesTheAccount() throws Exception {
        String sara = "sara@" + domain;
        String alphaAdmin = verifiedUser("Sara Admin", sara);
        String alpha = createWorkspace(alphaAdmin, "Alpha Partners");
        String layla = "layla@" + domain;
        String betaAdmin = verifiedUser("Layla Admin", layla);
        String beta = createWorkspace(betaAdmin, "Beta Search");

        String omar = "omar@" + domain;
        inviteAndAccept(login(sara), "Omar Member", omar, "MEMBER");
        mvc.perform(post("/api/v1/invitations")
                        .header("Authorization", "Bearer " + login(layla))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"email":"%s","role":"MEMBER"}]
                                """.formatted(omar)))
                .andExpect(status().isOk());
        String betaInvitation = db.queryForObject(
                "select id from app_lm_invitation where workspace_id = ?::uuid and email = ?", String.class, beta, omar);
        mvc.perform(post("/api/v1/onboarding/invitations/" + betaInvitation + "/accept")
                        .header("Authorization", "Bearer " + login(omar)))
                .andExpect(status().isOk());

        UUID omarId = userIdOf(omar);
        Session session = signIn(omar);
        String keyId = body(mvc.perform(post("/api/v1/workspace/api-keys")
                        .header("Authorization", "Bearer " + session.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"BI","kind":"PERSONAL","scopes":["projects:read"]}
                                """))
                .andExpect(status().isCreated())
                .andReturn()).at("/key/id").asText();
        db.update("insert into app_lm_notification_preference (user_id, workspace_id, kind, in_app, email) "
                + "values (?, ?::uuid, 'MENTIONED', true, false)", omarId, beta);
        db.update("insert into app_lm_member_availability (workspace_id, user_id) values (?::uuid, ?)", alpha, omarId);
        db.update("insert into app_lm_email_signature (workspace_id, user_id, name, layout, is_default) "
                + "values (?::uuid, ?, 'Work', 'CLASSIC', true)", alpha, omarId);
        db.update("update app_lm_user set phone = '+971 50 123 4567', linkedin_url = 'https://www.linkedin.com/in/omar' "
                + "where id = ?", omarId);
        String grant = "nylas-" + UUID.randomUUID();
        db.update("""
                insert into app_lm_mailbox_connection (workspace_id, user_id, provider, address, grant_id, status,
                    daily_cap, connected_at, gateway)
                values (?::uuid, ?, 'google', 'omar@mailbox.example', ?, 'ACTIVE', 50, now(), 'NYLAS')""",
                alpha, omarId, grant);
        db.update("insert into app_lm_user_avatar (user_id, token, content, content_type, sha256) "
                + "values (?, ?, decode('ffd8', 'hex'), 'image/jpeg', ?)", omarId, "t".repeat(43), "0".repeat(64));
        assertThat(countOf("app_lm_notification_preference", omarId)).as("the seeded rows are there to go").isOne();
        assertThat(countOf("app_lm_user_avatar", omarId)).isOne();
        assertThat(countOf("app_lm_member_availability", omarId)).isOne();
        assertThat(countOf("app_lm_refresh_token", omarId)).isPositive();

        JsonNode preview = body(mvc.perform(get("/api/v1/auth/me/deletion-preview")
                        .header("Authorization", "Bearer " + session.token()).cookie(session.refresh()))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(names(preview.get("workspaces"))).containsExactly("Alpha Partners", "Beta Search");
        assertThat(preview.get("soleAdminOf")).isEmpty();
        assertThat(preview.get("proof").asText()).isEqualTo("PASSWORD");
        assertThat(preview.get("codeRequired").asBoolean()).isFalse();
        email.clear();

        MvcResult deleted = deleteAccount(session, Map.of("currentPassword", PASSWORD));

        assertThat(deleted.getResponse().getStatus()).as(deleted.getResponse().getContentAsString()).isEqualTo(204);
        assertThat(deleted.getResponse().getHeader("Set-Cookie")).contains("lm_refresh=").contains("Max-Age=0");
        Map<String, Object> account = db.queryForMap(
                "select email, full_name, title, phone, linkedin_url, password_hash, status, deleted_at from app_lm_user "
                        + "where id = ?", omarId);
        assertThat(account.get("email")).isEqualTo("deleted-" + omarId + "@deleted.invalid");
        assertThat(account.get("full_name")).isEqualTo("Former member");
        assertThat(account.get("phone")).isNull();
        assertThat(account.get("linkedin_url")).isNull();
        assertThat(account.get("password_hash")).isNull();
        assertThat(account.get("status")).isEqualTo("DELETED");
        assertThat(account.get("deleted_at")).isNotNull();

        assertThat(db.queryForList("select status from app_lm_workspace_member where user_id = ?", String.class, omarId))
                .containsExactly("REMOVED", "REMOVED");
        assertThat(db.queryForList("""
                select workspace_id::text from app_lm_audit_event
                where event_type = 'MEMBER_REMOVED' and actor_user_id = ? and metadata ->> 'reason' = 'ACCOUNT_DELETED'
                """, String.class, omarId)).containsExactlyInAnyOrder(alpha, beta);
        assertThat(db.queryForObject("select revoked_reason from app_lm_api_key where id = ?::uuid", String.class, keyId))
                .isEqualTo("MEMBER_REMOVED");
        for (String table : List.of("app_lm_refresh_token", "app_lm_user_identity", "app_lm_verification_token",
                "app_lm_user_avatar", "app_lm_notification_preference", "app_lm_member_availability",
                "app_lm_email_signature")) {
            assertThat(countOf(table, omarId)).as(table).isZero();
        }
        // Let go at the provider once the deletion has committed, off the request.
        Awaitility.await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(mailboxes.revoked()).contains(grant));
        assertThat(countOf("app_lm_mailbox_connection", omarId)).isZero();
        assertThat(email.sent()).anySatisfy(message -> {
            assertThat(message.to()).isEqualTo(omar);
            assertThat(message.subject()).contains("account was deleted");
        });

        assertThat(codeOf(loginRaw(omar))).isEqualTo("INVALID_CREDENTIALS");
        mvc.perform(post("/api/v1/auth/refresh").cookie(session.refresh()).with(csrf()))
                .andExpect(status().isUnauthorized());
        assertThat(db.queryForObject("""
                select count(*) from app_lm_audit_event where event_type = 'ACCOUNT_DELETED' and actor_user_id = ?
                  and outcome = 'SUCCESS' and workspace_id is null""", Long.class, omarId)).isOne();

        // The same address can start again: a new account, nothing of the old one.
        String fresh = verifiedUser("Omar Again", omar);
        assertThat(userIdOf(omar)).isNotEqualTo(omarId);
        assertThat(fresh).isNotBlank();
    }

    @Test
    @DisplayName("a task held in another workspace than the session's goes to its lead, and the lead is told")
    void tasksInAnotherWorkspaceAreHandedToItsLead() throws Exception {
        String layla = "layla@" + domain;
        String betaId = createWorkspace(verifiedUser("Layla Lead", layla), "Beta Search");
        String omar = "omar@" + domain;
        inviteAndAccept(login(layla), "Omar Member", omar, "MEMBER");
        String sara = "sara@" + domain;
        createWorkspace(verifiedUser("Sara Admin", sara), "Alpha Partners");
        mvc.perform(post("/api/v1/invitations")
                        .header("Authorization", "Bearer " + login(sara))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"email":"%s","role":"MEMBER"}]
                                """.formatted(omar)))
                .andExpect(status().isOk());
        String alphaInvitation = db.queryForObject(
                "select id from app_lm_invitation where email = ? and status = 'PENDING'", String.class, omar);
        mvc.perform(post("/api/v1/onboarding/invitations/" + alphaInvitation + "/accept")
                        .header("Authorization", "Bearer " + login(omar)))
                .andExpect(status().isOk());

        String laylaToken = login(layla);
        String projectId = createProject(laylaToken, "Chief Operating Officer");
        mvc.perform(put("/api/v1/projects/" + projectId + "/members/" + memberIdOf(laylaToken, omar))
                        .header("Authorization", "Bearer " + laylaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"RESEARCHER"}"""))
                .andExpect(status().isOk());
        String personId = body(mvc.perform(post("/api/v1/projects/" + projectId + "/candidates")
                        .header("Authorization", "Bearer " + laylaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Noor Haddad"}"""))
                .andExpect(status().isCreated())
                .andReturn()).get("personId").asText();
        UUID omarId = userIdOf(omar);
        UUID laylaId = userIdOf(layla);
        UUID taskId = db.queryForObject("""
                insert into app_lm_task (workspace_id, project_id, person_id, title, kind, assignee_user_id, source)
                values (?::uuid, ?::uuid, ?::uuid, 'Call back', 'CALL', ?, 'MANUAL') returning id""",
                UUID.class, betaId, projectId, personId, omarId);

        Session session = signIn(omar);
        assertThat(body(mvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + session.token()))
                .andReturn()).at("/workspace/name").asText()).as("the session is in the other workspace")
                .isEqualTo("Alpha Partners");

        MvcResult deleted = deleteAccount(session, Map.of("currentPassword", PASSWORD));

        assertThat(deleted.getResponse().getStatus()).as(deleted.getResponse().getContentAsString()).isEqualTo(204);
        assertThat(db.queryForObject("select assignee_user_id from app_lm_task where id = ?", UUID.class, taskId))
                .isEqualTo(laylaId);
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(db.queryForObject("""
                select count(*) from app_lm_notification
                where workspace_id = ?::uuid and recipient_user_id = ? and kind = 'TASKS_MOVED'""",
                Long.class, betaId, laylaId)).isOne());
    }

    @Test
    @DisplayName("the only admin of a workspace is told which, and nothing changes until they hand it over")
    void theOnlyAdminIsRefused() throws Exception {
        String sara = "sara@" + domain;
        createWorkspace(verifiedUser("Sara Admin", sara), "Sole Admin & Co");
        Session session = signIn(sara);

        JsonNode preview = body(mvc.perform(get("/api/v1/auth/me/deletion-preview")
                        .header("Authorization", "Bearer " + session.token()).cookie(session.refresh()))
                .andReturn());
        assertThat(names(preview.get("soleAdminOf"))).containsExactly("Sole Admin & Co");

        MvcResult refused = deleteAccount(session, Map.of("currentPassword", PASSWORD));

        assertThat(refused.getResponse().getStatus()).isEqualTo(409);
        assertThat(codeOf(refused)).isEqualTo("ACCOUNT_HANDOVER_REQUIRED");
        assertThat(names(body(refused).get("soleAdminOf"))).containsExactly("Sole Admin & Co");
        assertThat(db.queryForObject("select status from app_lm_user where email = ?", String.class, sara))
                .isEqualTo("ACTIVE");
        assertThat(db.queryForObject("select count(*) from app_lm_workspace_member m join app_lm_user u "
                + "on u.id = m.user_id where u.email = ? and m.status = 'ACTIVE'", Long.class, sara)).isOne();
    }

    @Test
    @DisplayName("the only lead of a live position is named it, read in its own workspace")
    void theOnlyLeadIsRefused() throws Exception {
        String sara = "sara@" + domain;
        String admin = verifiedUser("Sara Admin", sara);
        createWorkspace(admin, "Lead Firm");
        String omar = "omar@" + domain;
        inviteAndAccept(login(sara), "Omar Member", omar, "MEMBER");
        Session session = signIn(omar);
        createProject(session.token(), "Group CFO");

        MvcResult refused = deleteAccount(session, Map.of("currentPassword", PASSWORD));

        assertThat(codeOf(refused)).isEqualTo("ACCOUNT_HANDOVER_REQUIRED");
        JsonNode position = body(refused).get("soleLeadOf").get(0);
        assertThat(position.get("title").asText()).isEqualTo("Group CFO");
        assertThat(position.get("workspaceName").asText()).isEqualTo("Lead Firm");
        assertThat(db.queryForObject("select status from app_lm_user where email = ?", String.class, omar))
                .isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("a wrong password is refused on its own field and deletes nothing")
    void aWrongPasswordDeletesNothing() throws Exception {
        String sara = "sara@" + domain;
        verifiedUser("Sara G", sara);
        Session session = signIn(sara);

        MvcResult refused = deleteAccount(session, Map.of("currentPassword", "notmine123"));
        assertThat(codeOf(refused)).isEqualTo("CURRENT_PASSWORD_INVALID");
        assertThat(body(refused).at("/fieldErrors/currentPassword").asText()).isNotBlank();

        MvcResult empty = deleteAccount(session, Map.of());
        assertThat(codeOf(empty)).isEqualTo("VALIDATION_FAILED");

        assertThat(db.queryForObject("select status from app_lm_user where email = ?", String.class, sara))
                .isEqualTo("ACTIVE");
        assertThat(codeOf(loginRaw(sara))).isNull();
    }

    @Test
    @DisplayName("with two-factor on, a password account gives a code too")
    void twoFactorAsksForTheCode() throws Exception {
        String sara = "sara@" + domain;
        verifiedUser("Sara G", sara);
        Session enrolling = signIn(sara);
        String secret = body(postAs(enrolling, "/api/v1/auth/mfa/setup", null)).get("secret").asText();
        postAs(enrolling, "/api/v1/auth/mfa/enable",
                Map.of("currentPassword", PASSWORD, "code", TestTotp.codeAt(secret, Instant.now())));
        db.update("update app_lm_user_mfa_factor set last_used_step = null "
                + "where user_id = (select id from app_lm_user where email = ?)", sara);

        assertThat(body(mvc.perform(get("/api/v1/auth/me/deletion-preview")
                        .header("Authorization", "Bearer " + enrolling.token()).cookie(enrolling.refresh()))
                .andReturn()).get("codeRequired").asBoolean()).isTrue();
        assertThat(codeOf(deleteAccount(enrolling, Map.of("currentPassword", PASSWORD, "code", wrongCode(secret)))))
                .isEqualTo("MFA_CODE_INVALID");

        db.update("update app_lm_user_mfa_factor set last_used_step = null "
                + "where user_id = (select id from app_lm_user where email = ?)", sara);
        UUID saraId = userIdOf(sara);
        MvcResult deleted = deleteAccount(enrolling,
                Map.of("currentPassword", PASSWORD, "code", TestTotp.codeAt(secret, Instant.now())));

        assertThat(deleted.getResponse().getStatus()).as(deleted.getResponse().getContentAsString()).isEqualTo(204);
        assertThat(countOf("app_lm_user_mfa_factor", saraId)).isZero();
        assertThat(countOf("app_lm_user_mfa_recovery_code", saraId)).isZero();
    }

    @Test
    @DisplayName("an account with no password deletes only moments after signing in")
    void providerOnlyAccountNeedsARecentSignIn() throws Exception {
        String sara = "sara@" + domain;
        verifiedUser("Sara G", sara);
        Session session = signIn(sara);
        db.update("update app_lm_user set password_hash = null where email = ?", sara);
        db.update("""
                update app_lm_refresh_token set created_at = now() - interval '11 minutes'
                where user_id = (select id from app_lm_user where email = ?)""", sara);

        JsonNode preview = body(mvc.perform(get("/api/v1/auth/me/deletion-preview")
                        .header("Authorization", "Bearer " + session.token()).cookie(session.refresh()))
                .andReturn());
        assertThat(preview.get("proof").asText()).isEqualTo("RECENT_SIGN_IN");
        assertThat(preview.get("signedInRecently").asBoolean()).isFalse();
        assertThat(codeOf(deleteAccount(session, Map.of()))).isEqualTo("ACCOUNT_SIGN_IN_AGAIN");

        db.update("""
                update app_lm_refresh_token set created_at = now()
                where user_id = (select id from app_lm_user where email = ?)""", sara);
        assertThat(deleteAccount(session, Map.of()).getResponse().getStatus()).isEqualTo(204);
        assertThat(db.queryForObject("select count(*) from app_lm_user where email = ?", Long.class, sara)).isZero();
    }

    @Test
    @DisplayName("Download my data is the account's own: profile, workspaces, keys, sessions and sign-ins")
    void exportCarriesTheAccount() throws Exception {
        String sara = "sara@" + domain;
        String admin = verifiedUser("Sara G", sara);
        String workspaceId = createWorkspace(admin, "Export Firm");
        Session session = signIn(sara);
        mvc.perform(post("/api/v1/workspace/api-keys")
                        .header("Authorization", "Bearer " + session.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Script","kind":"PERSONAL","scopes":["projects:read"]}
                                """))
                .andExpect(status().isCreated());

        MvcResult exported = mvc.perform(get("/api/v1/auth/me/export")
                        .header("Authorization", "Bearer " + session.token()).cookie(session.refresh()))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(exported.getResponse().getHeader("Content-Disposition")).contains("attachment")
                .contains("uncava-account-");
        JsonNode file = body(exported);
        assertThat(file.at("/account/email").asText()).isEqualTo(sara);
        assertThat(file.at("/consents/termsAcceptedAt").isNull()).isFalse();
        assertThat(file.at("/workspaces/0/workspaceName").asText()).isEqualTo("Export Firm");
        assertThat(file.at("/workspaces/0/roles/0").asText()).isEqualTo("ADMIN");
        assertThat(file.at("/workspaces/0/apiKeys/0/name").asText()).isEqualTo("Script");
        assertThat(file.at("/workspaces/0/apiKeys/0/tokenHint").asText()).isNotBlank();
        assertThat(file.get("sessions")).isNotEmpty();
        assertThat(file.get("signIns")).anySatisfy(signIn -> {
            assertThat(signIn.get("event").asText()).isEqualTo("LOGIN_SUCCEEDED");
            assertThat(signIn.get("workspaceId").asText()).isEqualTo(workspaceId);
        });
        assertThat(exported.getResponse().getContentAsString()).doesNotContain(PASSWORD);
        assertThat(db.queryForObject("""
                select count(*) from app_lm_audit_event where event_type = 'ACCOUNT_DATA_EXPORTED' and actor_user_id = ?
                """, Long.class, userIdOf(sara))).isOne();
    }

    // Helpers

    private record Session(String token, Cookie refresh) {
    }

    private Session signIn(String emailAddress) throws Exception {
        MvcResult result = loginRaw(emailAddress);
        return new Session(body(result).get("accessToken").asText(), result.getResponse().getCookie("lm_refresh"));
    }

    private MvcResult loginRaw(String emailAddress) throws Exception {
        return mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(emailAddress, PASSWORD)))
                .andReturn();
    }

    private MvcResult deleteAccount(Session session, Map<String, String> payload) throws Exception {
        return mvc.perform(delete("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + session.token())
                        .cookie(session.refresh())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new HashMap<>(payload))))
                .andReturn();
    }

    private MvcResult postAs(Session session, String path, Map<String, String> payload) throws Exception {
        var request = post(path).header("Authorization", "Bearer " + session.token())
                .cookie(session.refresh()).with(csrf());
        if (payload != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload));
        }
        return mvc.perform(request).andReturn();
    }

    private String createProject(String token, String title) throws Exception {
        String clientId = body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"Client %s"}""".formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
        return body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"%s"}
                                """.formatted(clientId, title)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    /** Six digits no step in the window around now produces. */
    private static String wrongCode(String secret) {
        long step = TestTotp.stepAt(Instant.now());
        java.util.Set<String> valid = java.util.Set.of(TestTotp.codeAtStep(secret, step - 1),
                TestTotp.codeAtStep(secret, step), TestTotp.codeAtStep(secret, step + 1),
                TestTotp.codeAtStep(secret, step + 2));
        for (int candidate = 0; ; candidate++) {
            String code = "%06d".formatted(candidate);
            if (!valid.contains(code)) {
                return code;
            }
        }
    }

    private UUID userIdOf(String emailAddress) {
        return db.queryForObject("select id from app_lm_user where email = ?", UUID.class, emailAddress);
    }

    private long countOf(String table, UUID userId) {
        return db.queryForObject("select count(*) from " + table + " where user_id = ?", Long.class, userId);
    }

    private static List<String> names(JsonNode workspaces) {
        return java.util.stream.StreamSupport.stream(workspaces.spliterator(), false)
                .map(workspace -> workspace.get("name").asText()).toList();
    }
}
