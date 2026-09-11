package app.lightmove.api.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

    @Test
    @DisplayName("never redirects an API call, whatever hostname it arrives on")
    void leavesTheApiAlone() throws Exception {
        filter.doFilter(request(OTHER_HOST, "/api/v1/auth/providers"), response, chain);

        assertThat(response.getRedirectedUrl()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    private static MockHttpServletRequest request(String host, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setServerName(host);
        return request;
    }
}
