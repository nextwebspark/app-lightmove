package app.lightmove.api.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The SPA is served from the same origin as the API. This is the test that keeps that from costing us
 * anything.
 *
 * <p>Serving both from one application is what makes the auth model work — a {@code SameSite=Strict},
 * host-only refresh cookie is only ever sent back to the host that served the page. But it means the
 * security chain that permits the SPA sits in front of the one that guards the API, and it matches by
 * <i>exclusion</i>: everything that is not {@code /api/}, Actuator, or an OAuth2 redirect. Get that
 * matcher wrong and it silently swallows the API — every endpoint public, every test still green,
 * because the API tests all send credentials and would never notice they had stopped being required.
 *
 * <p>So this asserts both halves, and the second half is the one that matters: the SPA renders, <b>and
 * the API is still shut</b>.
 */
@IntegrationTest
class SpaSecurityTest {

    @Autowired MockMvc mvc;

    // ── The SPA is served ─────────────────────────────────────────────────────

    /**
     * The root is Boot's own welcome-page mapping, which <i>forwards</i> to index.html rather than
     * serving it inline — so MockMvc records the forward and an empty body. That is the mock, not the
     * server: a real container resolves the forward and the bytes go out. Asserting on the forward is
     * therefore the honest assertion here; the tests below prove the bytes exist.
     */
    @Test
    @DisplayName("serves the shell at the root")
    void servesShellAtRoot() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("index.html"));

        mvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("spa-shell")));
    }

    /**
     * The history fallback, and the reason it is not optional.
     *
     * <p>{@code /auth/verify} is a client-side route — there is no file at that path and there never
     * will be. It is also the URL in every verification email, opened cold in a fresh tab. Without the
     * fallback it 404s, and signup is broken end to end while every other test stays green.
     */
    @Test
    @DisplayName("falls back to the shell for a client-side route opened directly")
    void fallsBackForClientSideRoute() throws Exception {
        mvc.perform(get("/auth/verify").param("token", "irrelevant"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("spa-shell")));

        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("spa-shell")));
    }

    /**
     * A missing asset must 404, not quietly hand back the shell.
     *
     * <p>Otherwise a stale {@code <script src>} pointing at a deleted content hash answers 200 with a
     * page of HTML, the browser tries to parse it as JavaScript, and the app dies with a syntax error
     * that names a line in a file that does not exist.
     */
    @Test
    @DisplayName("404s a missing asset rather than serving the shell")
    void missingAssetIsNotTheShell() throws Exception {
        mvc.perform(get("/assets/index-deadbeef.js"))
                .andExpect(status().isNotFound());
    }

    // ── The API is still shut ─────────────────────────────────────────────────

    /**
     * The line the SPA chain must not cross. If its matcher ever starts claiming {@code /api/}, this is
     * what fails — and nothing else would.
     */
    @Test
    @DisplayName("still refuses an unauthenticated call to a tenant endpoint")
    void apiRemainsAuthenticated() throws Exception {
        mvc.perform(get("/api/v1/onboarding/workspaces"))
                .andExpect(status().isUnauthorized());
    }

    /** An unknown API path is an API response — never a page of HTML with a 200 on it. */
    @Test
    @DisplayName("does not serve the shell for an unknown API path")
    void unknownApiPathIsNotTheShell() throws Exception {
        mvc.perform(get("/api/v1/no-such-endpoint"))
                .andExpect(status().is4xxClientError())
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString();
                    if (body.contains("spa-shell")) {
                        throw new AssertionError(
                                "An unknown /api path was answered with the SPA shell. The fallback "
                                        + "resolver's api/ guard is not doing its job.");
                    }
                });
    }

    /**
     * Actuator beyond health/info stays denied on the app port.
     *
     * <p>Cloud Run routes one port, so there Actuator moves onto the app socket and the port-based
     * fence in {@code SecurityConfig} stands itself down. The matcher-based one must still hold, or
     * {@code /actuator/prometheus} becomes readable by anyone on the internet.
     */
    @Test
    @DisplayName("keeps metrics denied on the application port")
    void metricsStayDenied() throws Exception {
        mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("still serves health, which is what Cloud Run probes")
    void healthIsOpen() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}
