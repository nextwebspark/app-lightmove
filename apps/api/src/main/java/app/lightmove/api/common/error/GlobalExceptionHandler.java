package app.lightmove.api.common.error;

import app.lightmove.api.common.logging.CorrelationId;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * The single place an exception becomes an HTTP response, in RFC 9457 {@code ProblemDetail} form.
 *
 * <p>Two rules run through all of it. Every response carries the correlation id, so a user can quote
 * it and we can find the exact request in the logs. And nothing that was not deliberately chosen for
 * the client gets into the body — an unexpected exception's message is logged in full and replaced
 * with an opaque 500, because stack traces and constraint names describe our schema to whoever asked.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final URI ERROR_TYPE_BASE = URI.create("https://lightmove.app/errors/");

    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApiException(ApiException ex, HttpServletRequest request) {
        ErrorCode code = ex.getCode();

        // 4xx is the API working as designed — a caller got something wrong. Only 5xx is our failure,
        // and only that deserves a stack trace and someone's attention.
        if (code.status().is5xxServerError()) {
            log.error("[{}] {} at {}", code, ex.getMessage(), request.getRequestURI(), ex);
        } else {
            log.debug("[{}] {} at {}", code, ex.getMessage(), request.getRequestURI());
        }

        return problem(code, code.defaultMessage());
    }

    /** Bean Validation failures, unpacked into a field → message map the form can render inline. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                // First message per field: a field with three broken constraints still only has room
                // for one line of red text under it.
                fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));

        ProblemDetail problem = problem(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultMessage());
        problem.setProperty("fieldErrors", fieldErrors);
        return problem;
    }

    /** Malformed JSON. Says nothing about which parser choked, or on what. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadable(HttpMessageNotReadableException ex) {
        log.debug("Malformed request body: {}", ex.getMessage());
        return problem(ErrorCode.VALIDATION_FAILED, "Request body could not be read");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException ex) {
        log.debug("Authentication failed: {}", ex.getMessage());
        return problem(ErrorCode.INVALID_CREDENTIALS, ErrorCode.INVALID_CREDENTIALS.defaultMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        log.debug("Access denied: {}", ex.getMessage());
        return problem(ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.defaultMessage());
    }

    /**
     * The catch-all. Anything reaching here is a bug: it was not anticipated, so we have no idea what
     * its message contains and must assume the worst.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception at {} {}", request.getMethod(), request.getRequestURI(), ex);
        return problem(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage());
    }

    private ProblemDetail problem(ErrorCode code, String detail) {
        HttpStatus status = code.status();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(ERROR_TYPE_BASE.resolve(code.name().toLowerCase().replace('_', '-')));
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", code.name());
        problem.setProperty("timestamp", Instant.now().toString());
        problem.setProperty("correlationId", CorrelationId.current());
        return problem;
    }
}
