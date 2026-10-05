package app.lightmove.api.core.logging.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Both trace headers are caller-suppliable: the documented shape is read, anything else is dropped. */
class CloudTraceContextTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

    @Test
    @DisplayName("a traceparent's hex span id passes through and its flags give the sampled bit")
    void readsTraceparent() {
        assertThat(CloudTraceContext.parse("00-" + TRACE_ID + "-00f067aa0ba902b7-01", null))
                .contains(new CloudTraceContext(TRACE_ID, "00f067aa0ba902b7", true));
        assertThat(CloudTraceContext.parse("00-" + TRACE_ID + "-00f067aa0ba902b7-00", null))
                .contains(new CloudTraceContext(TRACE_ID, "00f067aa0ba902b7", false));
    }

    @Test
    @DisplayName("X-Cloud-Trace-Context's decimal span id becomes 16 hex chars")
    void convertsTheDecimalSpanId() {
        assertThat(CloudTraceContext.parse(null, TRACE_ID + "/12345;o=1"))
                .contains(new CloudTraceContext(TRACE_ID, "0000000000003039", true));
        assertThat(CloudTraceContext.parse(null, TRACE_ID + "/18446744073709551615"))
                .contains(new CloudTraceContext(TRACE_ID, "ffffffffffffffff", false));
    }

    @Test
    @DisplayName("a malformed traceparent falls back to X-Cloud-Trace-Context")
    void fallsBackWhenTraceparentIsMalformed() {
        assertThat(CloudTraceContext.parse("garbage", TRACE_ID + "/1;o=0"))
                .contains(new CloudTraceContext(TRACE_ID, "0000000000000001", false));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "00-" + TRACE_ID + "-00f067aa0ba902b7",
            "01-" + TRACE_ID + "-00f067aa0ba902b7-01",
            "00-4BF92F3577B34DA6A3CE929D0E0E4736-00f067aa0ba902b7-01",
            "00-" + TRACE_ID + "-00f067aa0ba902-01",
            "00-00000000000000000000000000000000-00f067aa0ba902b7-01",
            "00-" + TRACE_ID + "-0000000000000000-01",
            "00-" + TRACE_ID + "-00f067aa0ba902b7-01\"},\"injected\":{\"",
    })
    @DisplayName("a malformed traceparent yields no trace")
    void rejectsMalformedTraceparent(String header) {
        assertThat(CloudTraceContext.parse(header, null)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            TRACE_ID,
            TRACE_ID + "/",
            TRACE_ID + "/abc",
            TRACE_ID + "/-1",
            TRACE_ID + "/0",
            TRACE_ID + "/18446744073709551616",
            TRACE_ID + "/12345;o=2",
            "4bf92f35/12345",
            "00000000000000000000000000000000/12345",
    })
    @DisplayName("a malformed X-Cloud-Trace-Context yields no trace")
    void rejectsMalformedCloudTraceContext(String header) {
        assertThat(CloudTraceContext.parse(null, header)).isEmpty();
    }

    @Test
    @DisplayName("no header, no trace")
    void absentHeadersYieldNothing() {
        assertThat(CloudTraceContext.parse(null, null)).isEmpty();
    }
}
