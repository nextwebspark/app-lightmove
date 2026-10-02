package app.lightmove.api.core.logging.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.lightmove.api.core.error.service.ClientDisconnects;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import jakarta.servlet.FilterChain;
import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import net.logstash.logback.argument.StructuredArgument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockAsyncContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** One INFO line per request, carrying the route template and a numeric status and duration. */
class RequestLogFilterTest {

    private static final String PROJECT_ROUTE = "/api/v1/projects/{projectId}";
    private static final HandlerMethod CONTROLLER_METHOD = controllerMethod();

    private final Logger logger = (Logger) LoggerFactory.getLogger(RequestLogFilter.class);
    private final ListAppender<ILoggingEvent> lines = new ListAppender<>();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @BeforeEach
    void attach() {
        lines.start();
        logger.addAppender(lines);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(lines);
        MDC.clear();
    }

    @Test
    @DisplayName("a matched request logs its route template, never the raw path, with numeric fields")
    void logsTheRouteTemplate() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET",
                "/api/v1/projects/6f1e8c5a-2b1d-4f3e-9a7c-0d2e4b6a8c1f");

        filter(null).doFilter(request, response, dispatchedTo(PROJECT_ROUTE, 200));

        ILoggingEvent line = theOnlyLine();
        assertThat(line.getLevel()).isEqualTo(Level.INFO);
        assertThat(line.getMessage()).isEqualTo("request");
        assertThat(fieldsOf(line))
                .contains("\"method\":\"GET\"", "\"route\":\"" + PROJECT_ROUTE + "\"", "\"status\":200")
                .containsPattern("\"durationMs\":\\d+")
                .doesNotContain("6f1e8c5a", "httpRequest");
    }

    @Test
    @DisplayName("a 500 is logged at INFO — the ERROR is the exception handler's, not this line's")
    void serverErrorStaysInfo() throws Exception {
        filter(null).doFilter(apiRequest(), response, dispatchedTo(PROJECT_ROUTE, 500));

        assertThat(theOnlyLine().getLevel()).isEqualTo(Level.INFO);
        assertThat(fieldsOf(theOnlyLine())).contains("\"status\":500");
    }

    @Test
    @DisplayName("an exception escaping the chain is logged as a 500 and still propagates")
    void escapingExceptionIsA500() {
        FilterChain failing = (req, res) -> {
            throw new IllegalStateException("boom");
        };

        assertThatThrownBy(() -> filter(null).doFilter(apiRequest(), response, failing))
                .isInstanceOf(IllegalStateException.class);
        assertThat(fieldsOf(theOnlyLine())).contains("\"status\":500");
    }

    @Test
    @DisplayName("a client that hung up is a 499, whether the error escaped or the handler swallowed it")
    void clientGoneIs499() throws Exception {
        FilterChain brokenPipe = (req, res) -> {
            throw new IOException("Broken pipe");
        };
        assertThatThrownBy(() -> filter(null).doFilter(apiRequest(), response, brokenPipe))
                .isInstanceOf(IOException.class);

        MockHttpServletRequest swallowed = apiRequest();
        filter(null).doFilter(swallowed, new MockHttpServletResponse(), (req, res) -> ClientDisconnects.markGone(req));

        assertThat(lines.list).hasSize(2).allSatisfy(line -> assertThat(fieldsOf(line)).contains("\"status\":499"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/health", "/actuator/health/liveness", "/assets/index-abc.js", "/projects"})
    @DisplayName("health probes and the SPA write no line")
    void skipsProbesAndTheSpa(String path) throws Exception {
        filter(null).doFilter(new MockHttpServletRequest("GET", path), response, (req, res) -> { });

        assertThat(lines.list).isEmpty();
    }

    @Test
    @DisplayName("a path no controller answers collapses to its first segment, so bots mint no series")
    void unmatchedIsCollapsed() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/wp-admin/../.env");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/**");

        filter(null).doFilter(request, response, (req, res) -> ((MockHttpServletResponse) res).setStatus(404));

        assertThat(fieldsOf(theOnlyLine())).contains("\"route\":\"/api/<unmatched>\"", "\"status\":404");
    }

    @Test
    @DisplayName("a request refused before dispatch is still attributed to its route")
    void refusalBeforeDispatchKeepsItsRoute() throws Exception {
        RequestMappingHandlerMapping mapping = mock(RequestMappingHandlerMapping.class);
        when(mapping.getHandler(any())).thenAnswer(call -> {
            markMatched(call.getArgument(0), PROJECT_ROUTE);
            return new HandlerExecutionChain(new Object());
        });

        filter(mapping).doFilter(apiRequest(), response, (req, res) -> ((MockHttpServletResponse) res).setStatus(401));

        assertThat(fieldsOf(theOnlyLine())).contains("\"route\":\"" + PROJECT_ROUTE + "\"", "\"status\":401");
    }

    @Test
    @DisplayName("a stream writes nothing at dispatch and one line when it ends, under the request's MDC")
    void streamLogsOnceWhenItEnds() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/projects/x/stream");
        request.setAsyncSupported(true);
        MDC.put(CorrelationId.MDC_KEY, "abc123");

        filter(null).doFilter(request, response, (req, res) -> {
            markMatched((MockHttpServletRequest) req, "/api/v1/projects/{projectId}/stream");
            req.startAsync();
        });
        assertThat(lines.list).as("nothing at dispatch").isEmpty();

        MDC.clear();
        ((MockAsyncContext) request.getAsyncContext()).complete();

        ILoggingEvent line = theOnlyLine();
        assertThat(fieldsOf(line)).contains("\"route\":\"/api/v1/projects/{projectId}/stream\"", "\"status\":200");
        assertThat(line.getMDCPropertyMap()).containsEntry(CorrelationId.MDC_KEY, "abc123");
        assertThat(MDC.getCopyOfContextMap()).as("the completing thread is left clean").isNullOrEmpty();
    }

    private static RequestLogFilter filter(RequestMappingHandlerMapping mapping) {
        @SuppressWarnings("unchecked")
        ObjectProvider<RequestMappingHandlerMapping> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mapping);
        return new RequestLogFilter(provider);
    }

    private static MockHttpServletRequest apiRequest() {
        return new MockHttpServletRequest("GET", "/api/v1/projects/6f1e8c5a-2b1d-4f3e-9a7c-0d2e4b6a8c1f");
    }

    private static FilterChain dispatchedTo(String pattern, int status) {
        return (req, res) -> {
            markMatched((MockHttpServletRequest) req, pattern);
            ((MockHttpServletResponse) res).setStatus(status);
        };
    }

    private static void markMatched(MockHttpServletRequest request, String pattern) {
        request.setAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE, CONTROLLER_METHOD);
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, pattern);
    }

    private static HandlerMethod controllerMethod() {
        try {
            return new HandlerMethod(new Object(), Object.class.getMethod("toString"));
        } catch (NoSuchMethodException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private ILoggingEvent theOnlyLine() {
        assertThat(lines.list).hasSize(1);
        return lines.list.getFirst();
    }

    /** The structured arguments rendered as the encoder renders them: a quoted number would show here. */
    private static String fieldsOf(ILoggingEvent line) {
        try {
            StringWriter json = new StringWriter();
            try (JsonGenerator generator = new JsonFactory().createGenerator(json)) {
                generator.writeStartObject();
                for (Object argument : List.of(line.getArgumentArray())) {
                    ((StructuredArgument) argument).writeTo(generator);
                }
                generator.writeEndObject();
            }
            return json.toString();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
