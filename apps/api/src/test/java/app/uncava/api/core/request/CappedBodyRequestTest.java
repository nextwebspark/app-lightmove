package app.uncava.api.core.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.uncava.api.core.request.service.CappedBodyRequest;
import app.uncava.api.core.request.service.RequestBodyTooLargeException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/** The cap holds while the body is read, so a chunked body that declares no length cannot slip past it. */
class CappedBodyRequestTest {

    @Test
    @DisplayName("a body that declares no length is still stopped once it passes the cap")
    void undeclaredLengthIsCappedAsItIsRead() {
        CappedBodyRequest capped = new CappedBodyRequest(chunked("x".repeat(64)), 16);

        assertThatThrownBy(() -> capped.getInputStream().readAllBytes())
                .isInstanceOf(RequestBodyTooLargeException.class);
    }

    @Test
    @DisplayName("a body within the cap reads whole")
    void bodyWithinTheCapReadsWhole() throws Exception {
        CappedBodyRequest capped = new CappedBodyRequest(chunked("{\"email\":\"a@b.co\"}"), 64);

        try (InputStream body = capped.getInputStream()) {
            assertThat(new String(body.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("{\"email\":\"a@b.co\"}");
        }
    }

    private static MockHttpServletRequest chunked(String body) {
        MockHttpServletRequest request = new MockHttpServletRequest() {
            @Override
            public long getContentLengthLong() {
                return -1;
            }

            @Override
            public int getContentLength() {
                return -1;
            }
        };
        request.addHeader("Transfer-Encoding", "chunked");
        request.setContentType("application/json");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }
}
