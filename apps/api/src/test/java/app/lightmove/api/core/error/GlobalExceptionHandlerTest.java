package app.lightmove.api.core.error;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.core.error.handler.GlobalExceptionHandler;
import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;

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

    private DataIntegrityViolationException violation(String constraintName) {
        return new DataIntegrityViolationException("insert failed",
                new ConstraintViolationException("duplicate key", new SQLException("23505"), constraintName));
    }
}
