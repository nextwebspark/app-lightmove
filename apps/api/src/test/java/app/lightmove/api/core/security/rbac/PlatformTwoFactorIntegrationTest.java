package app.lightmove.api.core.security.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import app.lightmove.api.IntegrationTest;
import app.lightmove.api.core.config.ApplicationVariables;
import app.lightmove.api.billing.BillingFlowSupport;
import app.lightmove.api.core.security.mfa.TestTotp;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.JsonNode;

/**
 * #1313 with {@code lightmove.platform.require-two-factor} on: a super admin needs a factor on every platform
 * route and a fresh code on the high-impact ones, and nobody else is told any of it exists.
 */
@IntegrationTest
@TestPropertySource(properties = "lightmove.platform.require-two-factor=true")
class PlatformTwoFactorIntegrationTest extends BillingFlowSupport {

    private static final String LIBRARY = "/api/v1/platform/position-templates";
    private static final String STEP_UP = "/api/v1/platform/step-up";
    private static final String GRANT = """
            {"source":"MANUAL","credits":10,"filsPerCredit":150,"externalRef":"%s"}
            """;

    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMappings;
    @Autowired ApplicationVariables variables;

    @Test
    @DisplayName("a super admin with no factor is refused on every platform route, by a code the console can act on")
    void superAdminWithoutAFactorIsRefusedEverywhere() throws Exception {
        UUID workspace = newWorkspace();
        Session admin = superAdminSession();

        List<MockHttpServletRequestBuilder> routes = List.of(
                get(LIBRARY),
                post("/api/v1/platform/workspaces/" + workspace + "/credit-grants")
                        .contentType(MediaType.APPLICATION_JSON).content(GRANT.formatted("REF-1")),
                put("/api/v1/platform/workspaces/" + workspace + "/subscription")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plan":"PRO","billingInterval":"ANNUAL","seats":5}
                                """),
                post("/api/v1/platform/workspaces/" + workspace + "/restore"),
                get("/api/v1/platform/workspaces/" + workspace + "/purge-certificate"),
                post(STEP_UP).contentType(MediaType.APPLICATION_JSON).content("""
                        {"code":"123456"}
                        """));

        for (MockHttpServletRequestBuilder route : routes) {
            expectRefused(403, "PLATFORM_TWO_FACTOR_REQUIRED", as(admin, route));
        }
        assertThat(ledger.balanceOf(workspace).available()).isZero();
    }

    @Test
    @DisplayName("every route under /api/v1/platform is behind the platform guard, so the factor is asked on all of them")
    void everyPlatformRouteIsGuarded() {
        List<String> unguarded = new ArrayList<>();
        handlerMappings.getHandlerMethods().forEach((mapping, handler) -> {
            boolean platformRoute = mapping.getPatternValues().stream()
                    .anyMatch(pattern -> pattern.startsWith("/api/v1/platform/"));
            PreAuthorize guard = AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(), PreAuthorize.class);
            if (platformRoute && (guard == null || !guard.value().startsWith("@platformAuthorizer."))) {
                unguarded.add(handler.toString());
            }
        });

        assertThat(unguarded).isEmpty();
    }

    @Test
    @DisplayName("with a factor, an ordinary platform route opens and a high-impact one asks for a fresh code first")
    void aFactorOpensTheConsoleAndAFreshCodeTheHighImpactActions() throws Exception {
        UUID workspace = newWorkspace();
        Session admin = superAdminSession();
        String secret = enrol(admin);

        assertThat(as(admin, get(LIBRARY)).getResponse().getStatus()).isEqualTo(200);
        expectRefused(403, "PLATFORM_STEP_UP_REQUIRED", grantCredits(admin, workspace, "REF-2"));
        assertThat(ledger.balanceOf(workspace).available()).isZero();

        assertThat(stepUp(admin, freshCode(admin, secret)).getResponse().getStatus()).isEqualTo(204);

        assertThat(grantCredits(admin, workspace, "REF-2").getResponse().getStatus()).isEqualTo(201);
        assertThat(ledger.balanceOf(workspace).available()).isEqualTo(10);
        assertThat(auditCount("PLATFORM_STEP_UP_PASSED", admin)).isEqualTo(1);
    }

    @Test
    @DisplayName("changing or resetting an application variable asks for a fresh code first")
    void anApplicationVariableChangeAsksForAFreshCode() throws Exception {
        String variable = "/api/v1/platform/settings/enrichment.sourcing.max-companies-per-run";
        Session admin = superAdminSession();
        String secret = enrol(admin);
        MockHttpServletRequestBuilder change = put(variable).contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":\"8\",\"note\":\"Larger groups\"}");
        MockHttpServletRequestBuilder reset = post(variable + "/reset").contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\":\"Back to what shipped\"}");

        try {
            assertThat(as(admin, get("/api/v1/platform/settings")).getResponse().getStatus()).isEqualTo(200);
            expectRefused(403, "PLATFORM_STEP_UP_REQUIRED", as(admin, change));
            expectRefused(403, "PLATFORM_STEP_UP_REQUIRED", as(admin, reset));

            stepUp(admin, freshCode(admin, secret));

            assertThat(as(admin, change).getResponse().getStatus()).isEqualTo(200);
            assertThat(as(admin, reset).getResponse().getStatus()).isEqualTo(200);
        } finally {
            db.update("DELETE FROM app_lm_platform_setting");
            db.execute("TRUNCATE app_lm_platform_setting_change");
            variables.reload();
        }
    }

    @Test
    @DisplayName("the fresh code lasts for the step-up window and no longer")
    void theWindowCloses() throws Exception {
        UUID workspace = newWorkspace();
        Session admin = superAdminSession();
        String secret = enrol(admin);
        stepUp(admin, freshCode(admin, secret));
        assertThat(grantCredits(admin, workspace, "REF-3").getResponse().getStatus()).isEqualTo(201);

        db.update("UPDATE app_lm_platform_step_up SET verified_at = now() - interval '11 minutes' WHERE user_id = "
                + "(SELECT id FROM app_lm_user WHERE email = ?)", admin.email());

        expectRefused(403, "PLATFORM_STEP_UP_REQUIRED", grantCredits(admin, workspace, "REF-4"));
        assertThat(ledger.balanceOf(workspace).available()).isEqualTo(10);
    }

    @Test
    @DisplayName("a code entered for an earlier factor does not carry over to a new one")
    void aCodeOlderThanTheFactorDoesNotCount() throws Exception {
        UUID workspace = newWorkspace();
        Session admin = superAdminSession();
        String secret = enrol(admin);
        stepUp(admin, freshCode(admin, secret));

        db.update("UPDATE app_lm_user_mfa_factor SET enabled_at = now() + interval '1 minute' WHERE user_id = "
                + "(SELECT id FROM app_lm_user WHERE email = ?)", admin.email());

        expectRefused(403, "PLATFORM_STEP_UP_REQUIRED", grantCredits(admin, workspace, "REF-5"));
    }

    @Test
    @DisplayName("a wrong code opens nothing and is audited; a recovery code is taken in its place")
    void aWrongCodeIsRefusedAndARecoveryCodeAccepted() throws Exception {
        UUID workspace = newWorkspace();
        Session admin = superAdminSession();
        List<String> recoveryCodes = new ArrayList<>();
        String secret = enrol(admin, recoveryCodes);

        expectRefused(400, "MFA_CODE_INVALID", stepUp(admin, wrongCode(secret)));
        expectRefused(403, "PLATFORM_STEP_UP_REQUIRED", grantCredits(admin, workspace, "REF-6"));
        assertThat(auditCount("PLATFORM_STEP_UP_FAILED", admin)).isEqualTo(1);

        assertThat(stepUp(admin, recoveryCodes.get(0)).getResponse().getStatus()).isEqualTo(204);
        assertThat(grantCredits(admin, workspace, "REF-6").getResponse().getStatus()).isEqualTo(201);
        expectRefused(400, "MFA_CODE_INVALID", stepUp(admin, recoveryCodes.get(0)));
    }

    @Test
    @DisplayName("a workspace admin is answered 404 on every platform route as before, and works in their workspace")
    void aWorkspaceUserIsUnaffected() throws Exception {
        UUID workspace = newWorkspace();
        String address = "admin@" + domain;
        createWorkspace(verifiedUser("Omar Khalil", address), "Own Firm");
        Session owner = sessionOf(address);

        expectRefused(404, "NOT_FOUND", as(owner, get(LIBRARY)));
        expectRefused(404, "NOT_FOUND", grantCredits(owner, workspace, "REF-7"));
        expectRefused(404, "NOT_FOUND", stepUp(owner, "123456"));
        assertThat(as(owner, get("/api/v1/projects")).getResponse().getStatus()).isEqualTo(200);
    }

    private record Session(String email, String token, Cookie refresh) {
    }

    private Session superAdminSession() throws Exception {
        String address = "platform@" + domain;
        createWorkspace(verifiedUser("Dana Aboud", address), "Platform Firm");
        db.update("""
                INSERT INTO app_lm_user_platform_role (user_id, role_id)
                SELECT u.id, r.id FROM app_lm_user u, app_lm_role r
                WHERE u.email = ? AND r.scope = 'PLATFORM' AND r.name = 'SUPER_ADMIN'
                """, address);
        return sessionOf(address);
    }

    private Session sessionOf(String address) throws Exception {
        MvcResult signedIn = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", address, "password", PASSWORD))))
                .andReturn();
        return new Session(address, body(signedIn).get("accessToken").asText(),
                signedIn.getResponse().getCookie("lm_refresh"));
    }

    private String enrol(Session session) throws Exception {
        return enrol(session, new ArrayList<>());
    }

    /** Turns two-factor on from the session, which it keeps: the access token goes on working. */
    private String enrol(Session session, List<String> recoveryCodes) throws Exception {
        String secret = body(mvc.perform(post("/api/v1/auth/mfa/setup")
                .header("Authorization", "Bearer " + session.token()).with(csrf()).cookie(session.refresh()))
                .andReturn()).get("secret").asText();
        JsonNode enabled = body(mvc.perform(post("/api/v1/auth/mfa/enable")
                .header("Authorization", "Bearer " + session.token()).with(csrf()).cookie(session.refresh())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("currentPassword", PASSWORD,
                        "code", TestTotp.codeAt(secret, Instant.now())))))
                .andReturn());
        assertThat(enabled.get("recoveryCodes")).as("enabling answered %s", enabled).isNotNull();
        enabled.get("recoveryCodes").forEach(code -> recoveryCodes.add(code.asText()));
        return secret;
    }

    /** A code for now, with the replay guard cleared: enrolling just spent this step's. */
    private String freshCode(Session session, String secret) {
        db.update("UPDATE app_lm_user_mfa_factor SET last_used_step = NULL WHERE user_id = "
                + "(SELECT id FROM app_lm_user WHERE email = ?)", session.email());
        return TestTotp.codeAt(secret, Instant.now());
    }

    private static String wrongCode(String secret) {
        long step = TestTotp.stepAt(Instant.now());
        Set<String> valid = Set.of(TestTotp.codeAtStep(secret, step - 1), TestTotp.codeAtStep(secret, step),
                TestTotp.codeAtStep(secret, step + 1), TestTotp.codeAtStep(secret, step + 2));
        for (int candidate = 0; ; candidate++) {
            String code = "%06d".formatted(candidate);
            if (!valid.contains(code)) {
                return code;
            }
        }
    }

    private MvcResult stepUp(Session session, String code) throws Exception {
        return as(session, post(STEP_UP).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("code", code))));
    }

    private MvcResult grantCredits(Session session, UUID workspace, String reference) throws Exception {
        return as(session, post("/api/v1/platform/workspaces/" + workspace + "/credit-grants")
                .contentType(MediaType.APPLICATION_JSON).content(GRANT.formatted(reference)));
    }

    private MvcResult as(Session session, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + session.token())).andReturn();
    }

    private void expectRefused(int status, String code, MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(status);
        assertThat(codeOf(result)).isEqualTo(code);
    }

    private long auditCount(String eventType, Session session) {
        return db.queryForObject("""
                SELECT count(*) FROM app_lm_audit_event
                WHERE event_type = ? AND actor_user_id = (SELECT id FROM app_lm_user WHERE email = ?)
                """, Long.class, eventType, session.email());
    }
}
