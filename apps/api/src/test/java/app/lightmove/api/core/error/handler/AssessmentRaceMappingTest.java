package app.lightmove.api.core.error.handler;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;

/** Two first saves of one assessment race the unique row: the loser hears the stale refusal, not a 500. */
class AssessmentRaceMappingTest {

    @Test
    @DisplayName("the scorecard's one-per-mapping constraint answers CANDIDATE_ASSESSMENT_STALE")
    void firstSaveRaceIsStale() {
        DataIntegrityViolationException raced = new DataIntegrityViolationException("duplicate key",
                new ConstraintViolationException("duplicate key", new SQLException("duplicate key", "23505"),
                        "app_lm_candidate_scorecard_candidate_uk"));

        ProblemDetail problem = new GlobalExceptionHandler()
                .handleDataIntegrity(raced, new MockHttpServletRequest("PUT", "/api/v1/projects/p/candidates/c/assessment"));

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getProperties()).containsEntry("code", "CANDIDATE_ASSESSMENT_STALE");
    }
}
