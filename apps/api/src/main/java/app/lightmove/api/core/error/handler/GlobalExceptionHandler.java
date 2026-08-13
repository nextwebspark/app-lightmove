package app.lightmove.api.core.error.handler;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.error.service.Problems;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
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
@Slf4j
public class GlobalExceptionHandler {

    private static final Pattern DISCONNECT_MESSAGE =
            Pattern.compile("Broken pipe|Connection reset|An established connection was aborted",
                    Pattern.CASE_INSENSITIVE);

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

        // The default is the code's own wording — a thrower's message is internal unless it was built
        // through ApiException.userFacing/withField, which is the deliberate opt-in for text a caller
        // is meant to read. Without that distinction every rule's message would be reflected, and
        // several of them quote the request.
        ProblemDetail problem = problem(code,
                ex.getClientDetail() == null ? code.defaultMessage() : ex.getClientDetail());
        if (ex.getFieldErrors() != null) {
            problem.setProperty("fieldErrors", ex.getFieldErrors());
        }
        return problem;
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
     * The body arrived as something we do not parse — {@code text/plain}, a form post, a missing
     * {@code Content-Type} on a JSON endpoint.
     *
     * <p>Third of the same family as {@link #handleNoResource} and {@link #handleMethodNotSupported},
     * and it affects every endpoint: without it, {@code curl -H 'Content-Type: text/plain'} against any
     * route answers <b>500</b> and writes an ERROR stack trace. That makes a malformed client request
     * indistinguishable from a real fault in our own alerting, and hands anyone a one-line recipe for
     * generating server errors at will.
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ProblemDetail handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex,
                                                     HttpServletRequest request) {
        log.debug("Unsupported content type {} at {} {} (supported: {})",
                ex.getContentType(), request.getMethod(), request.getRequestURI(), ex.getSupportedMediaTypes());
        return problem(ErrorCode.UNSUPPORTED_MEDIA_TYPE, ErrorCode.UNSUPPORTED_MEDIA_TYPE.defaultMessage());
    }

    /** The mirror of {@link #handleMediaTypeNotSupported} for a request's {@code Accept} header. */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ProblemDetail handleMediaTypeNotAcceptable(HttpMediaTypeNotAcceptableException ex,
                                                      HttpServletRequest request) {
        log.debug("Unacceptable Accept header at {} {} (we produce: {})",
                request.getMethod(), request.getRequestURI(), ex.getSupportedMediaTypes());
        return problem(ErrorCode.NOT_ACCEPTABLE, ErrorCode.NOT_ACCEPTABLE.defaultMessage());
    }

    /**
     * A database constraint beat a service-level pre-check — two requests raced. The pre-checks give
     * the common case a precise error; this maps the constraint that backs them to the same code, so a
     * race answers 409 instead of leaking a stack trace as a 500 and telling the client we broke.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        ErrorCode code = switch (constraintNameOf(ex)) {
            case "app_lm_client_workspace_name_uk" -> ErrorCode.CLIENT_ALREADY_EXISTS;
            default -> ErrorCode.CONFLICT;
        };
        log.info("[{}] constraint violation at {} {}", code, request.getMethod(), request.getRequestURI());
        return problem(code, code.defaultMessage());
    }

    /**
     * Two writes raced the same row's {@code @Version}: the second commit found the row already
     * advanced and updated zero rows. This is an expected concurrency event — two browser tabs, or two
     * teammates editing one project (PROJECT_EDIT is not seat-exclusive) — not a bug, so it is logged
     * at info and answered 409, like a raced unique constraint. The client's autosave serialises its
     * own writes to avoid it; this is the safety net for the races a single client cannot prevent.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException ex,
                                              HttpServletRequest request) {
        log.info("[{}] optimistic lock conflict at {} {}",
                ErrorCode.CONFLICT, request.getMethod(), request.getRequestURI());
        return problem(ErrorCode.CONFLICT, ErrorCode.CONFLICT.defaultMessage());
    }

    private String constraintNameOf(DataIntegrityViolationException ex) {
        for (Throwable cause = ex.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                    && violation.getConstraintName() != null) {
                return violation.getConstraintName();
            }
        }
        return "";
    }

    /**
     * The catch-all. Anything reaching here is a bug: it was not anticipated, so we have no idea what
     * its message contains and must assume the worst.
     *
     * <p>Except a client that hung up. The SPA cancels a Sourcing read the moment a Strategy save
     * supersedes it, so a half-written response is routine rather than a fault; logging each one at
     * error with a stack trace would bury the failures that really are ours.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        if (isClientDisconnect(ex)) {
            log.debug("Client hung up during {} {}", request.getMethod(), request.getRequestURI());
            // Returned but never written: the socket it would go to is already gone.
            return problem(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage());
        }
        log.error("Unhandled exception at {} {}", request.getMethod(), request.getRequestURI(), ex);
        return problem(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage());
    }

    /**
     * Whether the request died because the client went away rather than because we broke. Matched on
     * the cause chain: the write failure surfaces wrapped, as {@code HttpMessageNotWritableException}
     * around Tomcat's {@code ClientAbortException} around the socket's {@code IOException}.
     */
    private boolean isClientDisconnect(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof AsyncRequestNotUsableException
                    || cause.getClass().getSimpleName().equals("ClientAbortException")) {
                return true;
            }
            if (cause instanceof IOException && DISCONNECT_MESSAGE.matcher(String.valueOf(cause.getMessage())).find()) {
                return true;
            }
            if (cause.getCause() == cause) {
                return false;
            }
        }
        return false;
    }

    /** One definition of the error body, shared with ProblemAccessDeniedHandler. See {@link Problems}. */
    private ProblemDetail problem(ErrorCode code, String detail) {
        return Problems.of(code, detail);
    }
}
