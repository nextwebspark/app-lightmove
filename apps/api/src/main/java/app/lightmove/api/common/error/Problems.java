package app.lightmove.api.common.error;

import app.lightmove.api.common.logging.CorrelationId;
import java.net.URI;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * Builds the one error body this API has.
 *
 * <p>Extracted because there are now two places an error can be produced, and they must not drift.
 * {@link GlobalExceptionHandler} handles anything thrown inside a controller. But Spring Security denies
 * a request from inside the <i>filter chain</i>, long before the DispatcherServlet — so a
 * {@code @RestControllerAdvice} never sees it, and the response is whatever the security handler writes.
 * Both routes come through here, so both produce the same shape and the frontend has one contract.
 */
public final class Problems {

    private static final URI ERROR_TYPE_BASE = URI.create("https://lightmove.app/errors/");

    private Problems() {
    }

    public static ProblemDetail of(ErrorCode code, String detail) {
        HttpStatus status = code.status();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(ERROR_TYPE_BASE.resolve(code.name().toLowerCase().replace('_', '-')));
        problem.setTitle(status.getReasonPhrase());
        // `code` is the contract. The frontend switches on it and never on `detail`, so wording can
        // change without breaking a client.
        problem.setProperty("code", code.name());
        problem.setProperty("timestamp", Instant.now().toString());
        problem.setProperty("correlationId", CorrelationId.current());
        return problem;
    }

    public static ProblemDetail of(ErrorCode code) {
        return of(code, code.defaultMessage());
    }
}
