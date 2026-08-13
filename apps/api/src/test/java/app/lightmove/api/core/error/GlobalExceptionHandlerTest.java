package app.lightmove.api.core.error;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.handler.GlobalExceptionHandler;
import app.lightmove.api.core.error.model.ApiException;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/**
 * When a race beats a service pre-check, the DB constraint must surface as the same business error
 * the pre-check gives — not a 500 claiming we broke.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("a duplicate-client constraint answers CLIENT_ALREADY_EXISTS, same as the pre-check")
    void clientConstraintMapsToItsBusinessError() {
        ProblemDetail problem = handler.handleDataIntegrity(
                violation("app_lm_client_workspace_name_uk"), new MockHttpServletRequest());

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getProperties()).containsEntry("code", "CLIENT_ALREADY_EXISTS");
    }

    @Test
    @DisplayName("any other constraint answers a generic 409, never a 500")
    void unknownConstraintIsAConflictNotAServerError() {
        ProblemDetail problem = handler.handleDataIntegrity(
                violation("app_lm_project_member_lead_uk"), new MockHttpServletRequest());

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getProperties()).containsEntry("code", "CONFLICT");
    }

    @Test
    @DisplayName("an optimistic-lock race answers a generic 409, never a 500")
    void optimisticLockIsAConflictNotAServerError() {
        ProblemDetail problem = handler.handleOptimisticLock(
                new ObjectOptimisticLockingFailureException("app_lm_strategy", null),
                new MockHttpServletRequest());

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getProperties()).containsEntry("code", "CONFLICT");
    }

    @Test
    @DisplayName("a thrower's message stays internal unless it was written for the caller")
    void internalDetailIsNeverReflectedToTheClient() {
        // Several rules quote the request — an unknown sort token, a company label. Reflecting those
        // is exactly what this channel exists to prevent, so the default is the code's own wording.
        ProblemDetail problem = handler.handleApiException(
                new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown sort field: <script>"),
                new MockHttpServletRequest());

        assertThat(problem.getDetail()).isEqualTo(ErrorCode.VALIDATION_FAILED.defaultMessage());
        assertThat(problem.getProperties()).doesNotContainKey("fieldErrors");
    }

    @Test
    @DisplayName("a message written for the caller reaches them, as detail or under its field")
    void userFacingDetailIsRendered() {
        ProblemDetail banner = handler.handleApiException(
                ApiException.userFacing(ErrorCode.VALIDATION_FAILED,
                        "Clients are invited to a project, not granted through the roster"),
                new MockHttpServletRequest());

        assertThat(banner.getDetail()).isEqualTo("Clients are invited to a project, not granted through the roster");

        ProblemDetail field = handler.handleApiException(
                ApiException.withField(ErrorCode.VALIDATION_FAILED, "password", "Include at least one number"),
                new MockHttpServletRequest());

        // Same shape Bean Validation produces, so a service-level rule lands under the input like a
        // @Size does — which is what the password-length wording needed to reach the form at all.
        assertThat(field.getProperties()).containsEntry("fieldErrors",
                Map.of("password", "Include at least one number"));
    }

    @Test
    @DisplayName("a client that hung up mid-response is not logged as our failure")
    void clientDisconnectIsNotAnError() {
        // The SPA cancels a superseded Sourcing read on every criteria change; at error level each one
        // would arrive with a stack trace and bury the 500s that are actually ours.
        Exception brokenPipe = new HttpMessageNotWritableException("Could not write JSON",
                new IOException("Broken pipe"));

        assertThat(errorsLoggedBy(() -> handler.handleUnexpected(brokenPipe, new MockHttpServletRequest())))
                .isEmpty();
    }

    @Test
    @DisplayName("an unexpected exception is still logged as an error, with its stack trace")
    void genuineFailureIsStillAnError() {
        Exception bug = new IllegalStateException("no ObjectMapper bean");

        assertThat(errorsLoggedBy(() -> handler.handleUnexpected(bug, new MockHttpServletRequest())))
                .hasSize(1);
    }

    /** The ERROR-level events the handler emits while {@code call} runs. */
    private List<ILoggingEvent> errorsLoggedBy(Runnable call) {
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            call.run();
        } finally {
            logger.detachAppender(appender);
        }
        return appender.list.stream().filter(event -> event.getLevel() == Level.ERROR).toList();
    }

    private DataIntegrityViolationException violation(String constraintName) {
        return new DataIntegrityViolationException("insert failed",
                new ConstraintViolationException("duplicate key", new SQLException("23505"), constraintName));
    }
}
