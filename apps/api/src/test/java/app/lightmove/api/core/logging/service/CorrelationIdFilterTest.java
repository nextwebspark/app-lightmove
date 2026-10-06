package app.lightmove.api.core.logging.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.FilterChain;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** The inbound id is reused only when it is safe to store and echo; the MDC never outlives the request. */
class CorrelationIdFilterTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/projects");
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final Map<String, String> seenInsideTheChain = new HashMap<>();
    private final FilterChain recordingChain = (req, res) -> seenInsideTheChain.putAll(MDC.getCopyOfContextMap());

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("a well-formed inbound id is reused and echoed")
    void reusesAValidInboundId() throws Exception {
        request.addHeader(CorrelationId.HEADER, "abc_DEF-123");

        new CorrelationIdFilter("").doFilter(request, response, recordingChain);

        assertThat(response.getHeader(CorrelationId.HEADER)).isEqualTo("abc_DEF-123");
        assertThat(seenInsideTheChain).containsEntry(CorrelationId.MDC_KEY, "abc_DEF-123");
    }

    @ParameterizedTest
    @ValueSource(strings = {"../etc", "id with spaces", "<script>", "",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    @DisplayName("an inbound id that is too long or carries other characters is replaced, never echoed")
    void replacesAnInvalidInboundId(String inbound) throws Exception {
        request.addHeader(CorrelationId.HEADER, inbound);

        new CorrelationIdFilter("").doFilter(request, response, recordingChain);

        assertThat(response.getHeader(CorrelationId.HEADER)).hasSize(16).isNotEqualTo(inbound)
                .matches("[0-9a-f]{16}");
        assertThat(seenInsideTheChain.get(CorrelationId.MDC_KEY)).isEqualTo(response.getHeader(CorrelationId.HEADER));
    }

    @Test
    @DisplayName("with no inbound id one is generated")
    void generatesAnIdWhenAbsent() throws Exception {
        new CorrelationIdFilter("").doFilter(request, response, recordingChain);

        assertThat(response.getHeader(CorrelationId.HEADER)).matches("[0-9a-f]{16}");
    }

    @Test
    @DisplayName("a Cloud Run trace becomes the three trace keys when the project is known")
    void putsTheTraceWhenTheProjectIsKnown() throws Exception {
        request.addHeader("X-Cloud-Trace-Context", TRACE_ID + "/12345;o=1");

        new CorrelationIdFilter("test-proj").doFilter(request, response, recordingChain);

        assertThat(seenInsideTheChain)
                .containsEntry(CorrelationId.TRACE_KEY, "projects/test-proj/traces/" + TRACE_ID)
                .containsEntry(CorrelationId.SPAN_ID_KEY, "0000000000003039")
                .containsEntry(CorrelationId.TRACE_SAMPLED_KEY, "true");
    }

    @Test
    @DisplayName("no project id, no trace keys — a malformed field is worse than none")
    void writesNoTraceWithoutAProject() throws Exception {
        request.addHeader("traceparent", "00-" + TRACE_ID + "-00f067aa0ba902b7-01");

        new CorrelationIdFilter("").doFilter(request, response, recordingChain);

        assertThat(seenInsideTheChain).containsOnlyKeys(CorrelationId.MDC_KEY);
    }

    @Test
    @DisplayName("a malformed trace header writes no trace keys")
    void writesNoTraceForAMalformedHeader() throws Exception {
        request.addHeader("X-Cloud-Trace-Context", "not-a-trace");

        new CorrelationIdFilter("test-proj").doFilter(request, response, recordingChain);

        assertThat(seenInsideTheChain).containsOnlyKeys(CorrelationId.MDC_KEY);
    }

    @Test
    @DisplayName("the MDC is empty after the chain, including keys an inner filter added")
    void clearsTheMdcOnSuccess() throws Exception {
        request.addHeader("traceparent", "00-" + TRACE_ID + "-00f067aa0ba902b7-01");

        new CorrelationIdFilter("test-proj").doFilter(request, response, (req, res) -> MDC.put("userId", "u"));

        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    @DisplayName("the MDC is empty after the chain throws")
    void clearsTheMdcOnException() {
        request.addHeader("traceparent", "00-" + TRACE_ID + "-00f067aa0ba902b7-01");

        assertThatThrownBy(() -> new CorrelationIdFilter("test-proj").doFilter(request, response, (req, res) -> {
            MDC.put("userId", "u");
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }
}
