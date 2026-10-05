package app.lightmove.api.core.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.core.logging.service.CorrelationId;
import app.lightmove.api.core.logging.service.RequestLogFilter;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The real filter chain: the request line carries the route template and the caller's ids, which only
 * holds if the context filter sits in the security chain and the correlation filter clears last.
 */
@IntegrationTest
class RequestLoggingIntegrationTest extends FlowTestSupport {

    private final Logger logger = (Logger) LoggerFactory.getLogger(RequestLogFilter.class);
    private final ListAppender<ILoggingEvent> lines = new ListAppender<>();

    @BeforeEach
    void attach() {
        lines.start();
        logger.addAppender(lines);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(lines);
    }

    @Test
    @DisplayName("a signed-in project request's line carries the template, the user, the workspace and the project")
    void signedInProjectRequestCarriesItsIds() throws Exception {
        String adminEmail = "alok@" + domain;
        String workspaceId = createWorkspace(verifiedUser("Alok Kumar", adminEmail), "Logging Firm " + domain);
        String token = login(adminEmail);
        String userId = body(mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andReturn()).get("id").asText();
        UUID projectId = UUID.randomUUID();
        lines.list.clear();

        MvcResult result = mvc.perform(get("/api/v1/projects/{projectId}/activity", projectId)
                .header("Authorization", "Bearer " + token)).andReturn();

        ILoggingEvent line = requestLine();
        assertThat(line.getArgumentArray()).extracting(Object::toString)
                .contains("route=/api/v1/projects/{projectId}/activity", "status=" + result.getResponse().getStatus());
        assertThat(line.getMDCPropertyMap())
                .containsEntry(CorrelationId.USER_ID_KEY, userId)
                .containsEntry(CorrelationId.WORKSPACE_ID_KEY, workspaceId)
                .containsEntry(CorrelationId.PROJECT_ID_KEY, projectId.toString())
                .containsEntry(CorrelationId.MDC_KEY, result.getResponse().getHeader(CorrelationId.HEADER));
        assertThat(MDC.getCopyOfContextMap()).as("nothing outlives the request").isNullOrEmpty();
    }

    @Test
    @DisplayName("an anonymous refusal is still attributed to its route, and carries no user")
    void anonymousRefusalKeepsItsRoute() throws Exception {
        mvc.perform(get("/api/v1/projects/{projectId}/activity", UUID.randomUUID()));

        ILoggingEvent line = requestLine();
        assertThat(line.getArgumentArray()).extracting(Object::toString)
                .contains("route=/api/v1/projects/{projectId}/activity", "status=401");
        assertThat(line.getMDCPropertyMap()).containsOnlyKeys(CorrelationId.MDC_KEY);
    }

    private ILoggingEvent requestLine() {
        assertThat(lines.list).filteredOn(event -> event.getMessage().equals("request")).hasSize(1);
        return lines.list.stream().filter(event -> event.getMessage().equals("request")).findFirst().orElseThrow();
    }
}
