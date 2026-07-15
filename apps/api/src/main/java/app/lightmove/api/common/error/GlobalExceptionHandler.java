package app.lightmove.api.common.error;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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

    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApiException(ApiException ex, HttpServletRequest request) {
        ErrorCode code = ex.getCode();

        // A 5xx is our failure and gets a stack trace. A 4xx is the API working as designed, but it is
        // still the thing an operator is staring at when a user says "it just says 400" — so it gets
        // one line naming the rule that fired. The code and the URI only; the message may quote input.
        if (code.status().is5xxServerError()) {
            log.error("[{}] {} at {}", code, ex.getMessage(), request.getRequestURI(), ex);
        } else {
            log.info("[{}] {} {} → {}", code, request.getMethod(), request.getRequestURI(), code.status().value());
            log.debug("[{}] {}", code, ex.getMessage());
        }

        return problem(code, code.defaultMessage());
    }

    /** Bean Validation failures, unpacked into a field → message map the form can render inline. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                // First message per field: a field with three broken constraints still only has room
                // for one line of red text under it.
                fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));

        // Field names, never their values — a rejected password is still a password.
        log.info("[VALIDATION_FAILED] {} {} → 400 on {}",
                request.getMethod(), request.getRequestURI(), fieldErrors.keySet());

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

    /**
     * A required query parameter or header is missing, or will not convert to its declared type.
     *
     * <p>These are client mistakes and have to answer 400. Without this they land in the catch-all
     * below and become a <b>500</b> — the caller is told "Something went wrong on our end" for a
     * request that was simply wrong, and we log our own ERROR with a stack trace for it. A truncated
     * verification link (`/auth/verify` with no token) does exactly that.
     */
    @ExceptionHandler({ServletRequestBindingException.class, MethodArgumentTypeMismatchException.class})
    public ProblemDetail handleBadRequestBinding(Exception ex, HttpServletRequest request) {
        log.debug("Bad request binding at {} {}: {}",
                request.getMethod(), request.getRequestURI(), ex.getMessage());
        return problem(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultMessage());
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
     * No handler and no file at that path.
     *
     * <p>Without this it falls into the catch-all below and becomes a <b>500</b>, logged at ERROR with a
     * stack trace — for what is simply a wrong URL. That was survivable while every path was an API
     * route; now that this application also serves the SPA, "no such path" is a normal event. Every bot
     * probing for {@code /wp-login.php} would otherwise be recorded as an unhandled server bug, and the
     * one real 500 would be buried among them.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        log.debug("No resource at {} {}", request.getMethod(), request.getRequestURI());
        return problem(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.defaultMessage());
    }

    /**
     * The route exists, but not for that verb — a browser opening {@code /api/v1/auth/signup} in the
     * address bar, say, which is a GET of a POST-only endpoint.
     *
     * <p>Same reasoning as {@link #handleNoResource}: without this it lands in the catch-all and is
     * reported as a <b>500 INTERNAL_ERROR</b>, complete with an ERROR-level stack trace. That is a lie
     * twice over — nothing on our side is broken, and the client is told to retry a request that can
     * never succeed instead of being told to fix its verb.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                  HttpServletRequest request) {
        log.debug("Method {} not supported at {} (supported: {})",
                request.getMethod(), request.getRequestURI(), ex.getSupportedHttpMethods());
        return problem(ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED.defaultMessage());
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

    /** One definition of the error body, shared with ProblemAccessDeniedHandler. See {@link Problems}. */
    private ProblemDetail problem(ErrorCode code, String detail) {
        return Problems.of(code, detail);
    }
}
