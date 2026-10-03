package app.lightmove.api.core.logging.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.WorkspaceRole;
import jakarta.servlet.FilterChain;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerMapping;

/** The log lines of a signed-in request say whose they are — by id, never by email. */
class RequestContextFilterTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID WORKSPACE = UUID.randomUUID();

    private final Map<String, String> seenInsideTheChain = new HashMap<>();
    private final FilterChain recordingChain = (req, res) -> {
        Map<String, String> context = MDC.getCopyOfContextMap();
        if (context != null) {
            seenInsideTheChain.putAll(context);
        }
    };

    @AfterEach
    void reset() {
        MDC.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("a signed-in caller's user and workspace ids are put, and nothing else")
    void putsTheUserAndWorkspace() throws Exception {
        signIn(new AuthPrincipal(USER, "alok@firm.example", WORKSPACE, Set.of(WorkspaceRole.ADMIN), true));

        filter();

        assertThat(seenInsideTheChain).containsOnly(
                Map.entry(CorrelationId.USER_ID_KEY, USER.toString()),
                Map.entry(CorrelationId.WORKSPACE_ID_KEY, WORKSPACE.toString()));
        assertThat(seenInsideTheChain.values()).noneMatch(value -> value.contains("@"));
    }

    @Test
    @DisplayName("before onboarding there is no workspace, and no empty key stands in for one")
    void omitsAMissingWorkspace() throws Exception {
        signIn(new AuthPrincipal(USER, "alok@firm.example", null, Set.of(), true));

        filter();

        assertThat(seenInsideTheChain).containsOnlyKeys(CorrelationId.USER_ID_KEY);
    }

    @Test
    @DisplayName("an anonymous request puts nothing")
    void anonymousPutsNothing() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        filter();

        assertThat(seenInsideTheChain).isEmpty();
    }

    @Test
    @DisplayName("a project route's id is put only when it is a UUID")
    void projectIdIsPutOnlyWhenWellFormed() {
        UUID project = UUID.randomUUID();
        ProjectIdInterceptor interceptor = new ProjectIdInterceptor();

        MockHttpServletRequest junk = new MockHttpServletRequest();
        junk.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("projectId", "\"},{\"x"));
        interceptor.preHandle(junk, new MockHttpServletResponse(), new Object());
        assertThat(MDC.get(CorrelationId.PROJECT_ID_KEY)).isNull();

        MockHttpServletRequest real = new MockHttpServletRequest();
        real.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("projectId", project.toString()));
        interceptor.preHandle(real, new MockHttpServletResponse(), new Object());
        assertThat(MDC.get(CorrelationId.PROJECT_ID_KEY)).isEqualTo(project.toString());
    }

    private void signIn(AuthPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    }

    private void filter() throws Exception {
        new RequestContextFilter().doFilter(new MockHttpServletRequest("GET", "/api/v1/me"),
                new MockHttpServletResponse(), recordingChain);
    }
}
