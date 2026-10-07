package app.lightmove.api.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.core.security.apikey.PublicApiProblemWriter;
import app.lightmove.api.mcp.service.McpRequestLimitFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/** The body cap whichever way a body arrives: with its length declared, or sent without one. */
class McpRequestLimitFilterTest {

    private static final int CAP = 64;

    private final McpRequestLimitFilter filter = new McpRequestLimitFilter(CAP,
            new PublicApiProblemWriter(JsonMapper.builder().build()));

    @Test
    @DisplayName("a body over the cap is a 413 when its length is declared, and never reaches the transport")
    void declaredOversizeRefused() throws Exception {
        MockHttpServletResponse response = run(request("x".repeat(CAP + 1), true), new AtomicReference<>());
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("MCP_REQUEST_TOO_LARGE");
    }

    @Test
    @DisplayName("a body over the cap is a 413 when it is sent with no length, and never reaches the transport")
    void undeclaredOversizeRefused() throws Exception {
        AtomicReference<HttpServletRequest> reached = new AtomicReference<>();
        MockHttpServletResponse response = run(request("x".repeat(CAP * 10), false), reached);
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(reached.get()).isNull();
    }

    @Test
    @DisplayName("a body within the cap is replayed whole, to the stream and to the reader")
    void bodyWithinCapReplayed() throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}";
        AtomicReference<HttpServletRequest> reached = new AtomicReference<>();
        assertThat(run(request(body, false), reached).getStatus()).isEqualTo(200);
        assertThat(new String(reached.get().getInputStream().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(body);
        assertThat(reached.get().getReader().readLine()).isEqualTo(body);
    }

    private MockHttpServletResponse run(MockHttpServletRequest request, AtomicReference<HttpServletRequest> reached)
            throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (passed, answered) -> reached.set((HttpServletRequest) passed));
        return response;
    }

    private static MockHttpServletRequest request(String body, boolean declareLength) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/mcp") {
            @Override
            public int getContentLength() {
                return declareLength ? super.getContentLength() : -1;
            }

            @Override
            public long getContentLengthLong() {
                return declareLength ? super.getContentLengthLong() : -1;
            }
        };
        request.setContentType("application/json");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }
}
