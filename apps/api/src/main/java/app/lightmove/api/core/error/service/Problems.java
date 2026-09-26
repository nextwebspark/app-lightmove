package app.lightmove.api.core.error.service;
import app.lightmove.api.core.error.constant.ErrorCode;

import app.lightmove.api.core.logging.service.CorrelationId;
import java.net.URI;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * Builds the API's one error body, shared by {@link GlobalExceptionHandler} and the security filter
 * chain's handlers — which deny before the DispatcherServlet — so both keep one shape.
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
