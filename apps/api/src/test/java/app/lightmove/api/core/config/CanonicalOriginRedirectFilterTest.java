package app.lightmove.api.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** A page on a second hostname is sent to the base URL; the API and the canonical host are left alone. */
class CanonicalOriginRedirectFilterTest {

    private static final String BASE_URL = "https://lightmove-52hjvmu2bq-uc.a.run.app";
    private static final String OTHER_HOST = "lightmove-586609281886.us-central1.run.app";

    private final CanonicalOriginRedirectFilter filter = new CanonicalOriginRedirectFilter(
            new LightMoveProperties(null, null,
                    new WebSettings(BASE_URL, List.of(BASE_URL), "/auth/callback", 0),
                    null, null, null, null, null, null, null, null, null));

    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final MockFilterChain chain = new MockFilterChain();

    @Test
    @DisplayName("sends a page opened on another hostname to the base URL, path and query intact")
    void redirectsAPageOnAnotherHostname() throws Exception {
        MockHttpServletRequest request = request(OTHER_HOST, "/login");
        request.setQueryString("error=OAUTH_FAILED");

        filter.doFilter(request, response, chain);

        assertThat(response.getRedirectedUrl()).isEqualTo(BASE_URL + "/login?error=OAUTH_FAILED");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("serves a page opened on the base URL's own hostname")
    void servesTheCanonicalHostname() throws Exception {
        filter.doFilter(request("lightmove-52hjvmu2bq-uc.a.run.app", "/login"), response, chain);

        assertThat(response.getRedirectedUrl()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/auth/providers",
            "/actuator/health",
            "/oauth2/authorization/linkedin",
            "/login/oauth2/code/linkedin"})
    @DisplayName("never redirects the API, Actuator or the OAuth endpoints, whatever hostname they arrive on")
    void leavesNonSpaPathsAlone(String path) throws Exception {
        filter.doFilter(request(OTHER_HOST, path), response, chain);

        assertThat(response.getRedirectedUrl()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    private static MockHttpServletRequest request(String host, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setServerName(host);
        return request;
    }
}
