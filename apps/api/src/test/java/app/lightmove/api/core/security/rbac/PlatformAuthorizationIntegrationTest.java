package app.lightmove.api.core.security.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.IntegrationTest;
import app.lightmove.api.positiontemplate.PositionTemplateFlowSupport;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

/**
 * The platform tier: who reaches the shared library, how they learn they can, and that the role opens
 * nothing inside any workspace.
 */
@IntegrationTest
class PlatformAuthorizationIntegrationTest extends PositionTemplateFlowSupport {

    private static final List<HttpMethod> EVERY_METHOD = List.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT,
            HttpMethod.PATCH, HttpMethod.DELETE, HttpMethod.OPTIONS);

    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMappings;

    @Test
    @DisplayName("no route under /api/v1/platform admits to existing to a workspace admin, a member or a client")
    void noPlatformRouteAdmitsToExisting() throws Exception {
        Firm firm = firm("Sweep Firm", "admin");
        inviteAndAccept(firm.token(), "Mira Member", "mira@" + domain, "MEMBER");
        Map<String, String> outsiders = Map.of(
                "workspace admin", firm.token(),
                "member", login("mira@" + domain),
                "client", clientRepresentative(firm.token(), "Carl Client", "carl@client-" + domain));
        List<String> admitted = new ArrayList<>();
        int swept = 0;

        for (Map.Entry<RequestMappingInfo, HandlerMethod> route : handlerMappings.getHandlerMethods().entrySet()) {
            for (String pattern : route.getKey().getPatternValues()) {
                if (!pattern.startsWith("/api/v1/platform/")) {
                    continue;
                }
                swept++;
                String path = pathOf(pattern, route.getValue());
                // Every method, not only the route's own: a wrong one answered 405 would say the path is a route.
                for (HttpMethod method : EVERY_METHOD) {
                    for (Map.Entry<String, String> outsider : outsiders.entrySet()) {
                        int answered = mvc.perform(request(method, path)
                                        .header("Authorization", bearer(outsider.getValue()))
                                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                                .andReturn().getResponse().getStatus();
                        if (answered != 404) {
                            admitted.add(method + " " + pattern + " answered a " + outsider.getKey() + " " + answered);
                        }
                    }
                }
            }
        }

        assertThat(swept).as("the sweep found the platform routes").isPositive();
        assertThat(admitted).isEmpty();
    }

    @Test
    @DisplayName("a platform route and a path that is no route answer an outsider alike, whatever the method")
    void aRouteAndNoRouteAnswerAlike() throws Exception {
        Firm firm = firm("Probe Method Firm", "admin");

        for (String path : List.of("/api/v1/platform/settings", "/api/v1/platform/no-such-route")) {
            for (HttpMethod method : EVERY_METHOD) {
                MvcResult answered = mvc.perform(request(method, path).header("Authorization", bearer(firm.token())))
                        .andExpect(status().isNotFound())
                        .andReturn();
                assertThat(answered.getResponse().getHeader("Allow")).as(method + " " + path).isNull();
                assertThat(body(answered).get("code").asText()).isEqualTo("NOT_FOUND");
            }
        }
    }

    @Test
    @DisplayName("with no session a platform route answers 401, as every route does, and a super admin still gets in")
    void theGateAdmitsOnlyAPlatformRoleHolder() throws Exception {
        mvc.perform(get("/api/v1/platform/settings")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/platform/no-such-route")).andExpect(status().isUnauthorized());

        String owner = superAdmin();
        mvc.perform(get(LIBRARY).header("Authorization", bearer(owner))).andExpect(status().isOk());
        mvc.perform(delete(LIBRARY).header("Authorization", bearer(owner)))
                .andExpect(status().isMethodNotAllowed());
    }

    /** The pattern with each variable given a value of its handler's own type, so the request reaches the guard. */
    private static String pathOf(String pattern, HandlerMethod handler) {
        Map<String, String> values = new HashMap<>();
        for (MethodParameter parameter : handler.getMethodParameters()) {
            PathVariable variable = parameter.getParameterAnnotation(PathVariable.class);
            if (variable == null) {
                continue;
            }
            parameter.initParameterNameDiscovery(new DefaultParameterNameDiscoverer());
            String declared = variable.name().isEmpty() ? variable.value() : variable.name();
            Class<?> type = parameter.getParameterType();
            values.put(declared.isEmpty() ? parameter.getParameterName() : declared,
                    type == UUID.class ? UUID.randomUUID().toString() : type == String.class ? "anything" : "1");
        }
        return UriComponentsBuilder.fromPath(pattern).buildAndExpand(values).toUriString();
    }

    @Test
    @DisplayName("a workspace admin is not a super admin, and the library endpoints do not admit to existing")
    void workspaceAdminCannotReachTheLibrary() throws Exception {
        Firm firm = firm("Probe Firm", "alok");

        mvc.perform(get(LIBRARY).header("Authorization", bearer(firm.token())))
                .andExpect(status().isNotFound());
        expectRefused(404, "NOT_FOUND", putJson(firm.token(), LIBRARY + "/chief-financial-officer",
                templateRequest("Chief Financial Officer", uniqueKeyword(), "Finance", 0L)));
        assertThat(getJson(firm.token(), "/api/v1/auth/me").get("platformActions").size()).isZero();
    }

    @Test
    @DisplayName("a granted super admin reaches the library on the token they already hold, and /me says so")
    void grantedSuperAdminReachesTheLibrary() throws Exception {
        Firm owner = firm("Owner Firm", "owner");
        grantSuperAdmin(owner.email());

        assertThat(getJson(owner.token(), LIBRARY).size()).isGreaterThanOrEqualTo(17);
        assertThat(getJson(owner.token(), "/api/v1/auth/me").get("platformActions").get(0).asText())
                .isEqualTo("TEMPLATE_LIBRARY_MANAGE");
    }

    @Test
    @DisplayName("a platform action seeded by a newer build is left out of /me rather than failing every auth response")
    void platformActionUnknownToThisBuildIsSkipped() throws Exception {
        String owner = superAdmin();
        db.update("insert into app_lm_action (scope, name, description) "
                + "values ('PLATFORM', 'FROM_A_NEWER_BUILD', 'Seeded by a migration this build predates')");
        try {
            db.update("""
                    insert into app_lm_role_action (role_id, action_id)
                    select r.id, a.id from app_lm_role r, app_lm_action a
                    where r.scope = 'PLATFORM' and r.name = 'SUPER_ADMIN'
                      and a.scope = 'PLATFORM' and a.name = 'FROM_A_NEWER_BUILD'
                    """);

            JsonNode actions = getJson(owner, "/api/v1/auth/me").get("platformActions");
            assertThat(actions.valueStream().map(JsonNode::asText).toList())
                    .containsExactly("TEMPLATE_LIBRARY_MANAGE", "CREDIT_GRANT", "SUBSCRIPTION_MANAGE",
                            "WORKSPACE_LIFECYCLE_MANAGE", "PROMPT_MANAGE", "PROMPT_PROMOTE", "EVAL_RUN",
                            "PLATFORM_SETTINGS_MANAGE");
        } finally {
            db.update("delete from app_lm_role_action where action_id in "
                    + "(select id from app_lm_action where name = 'FROM_A_NEWER_BUILD')");
            db.update("delete from app_lm_action where name = 'FROM_A_NEWER_BUILD'");
        }
    }

    @Test
    @DisplayName("the platform role opens nothing inside another firm's workspace")
    void superAdminReadsNoTenantData() throws Exception {
        Firm neighbour = firm("Neighbour Firm", "sara");
        String theirProject = createProject(neighbour.token(), "Chief Financial Officer");
        String owner = superAdmin();

        mvc.perform(get("/api/v1/projects/" + theirProject + "/position").header("Authorization", bearer(owner)))
                .andExpect(status().isNotFound());
    }
}
