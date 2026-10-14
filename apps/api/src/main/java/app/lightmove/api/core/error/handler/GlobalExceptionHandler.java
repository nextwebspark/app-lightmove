package app.lightmove.api.core.error.handler;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.error.service.ClientDisconnects;
import app.lightmove.api.core.error.service.Problems;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

/**
 * The single place an exception becomes an RFC 9457 {@code ProblemDetail}. Nothing not chosen for the
 * client reaches the body. Most handlers exist so a client mistake does not fall into the catch-all 500.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /** Postgres' check violation, value too long for its column, and numeric value out of range. */
    private static final Set<String> REFUSED_VALUE_STATES = Set.of("23514", "22001", "22003");

    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApiException(ApiException ex, HttpServletRequest request) {
        ErrorCode code = ex.getCode();

        // A 4xx logs the code and URI only at info: the message may quote input.
        if (code.status().is5xxServerError()) {
            log.error("[{}] {} at {}", code, ex.getMessage(), request.getRequestURI(), ex);
        } else {
            log.info("[{}] {} {} → {}", code, request.getMethod(), request.getRequestURI(), code.status().value());
            log.debug("[{}] {}", code, ex.getMessage());
        }

        // A thrower's message is internal unless built through userFacing/withField: several quote the request.
        ProblemDetail problem = problem(code,
                ex.getClientDetail() == null ? code.defaultMessage() : ex.getClientDetail());
        if (ex.getFieldErrors() != null) {
            problem.setProperty("fieldErrors", ex.getFieldErrors());
        }
        if (ex.getProperties() != null) {
            ex.getProperties().forEach(problem::setProperty);
        }
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));

        // Field names, never their values — a rejected password is still a password.
        log.info("[VALIDATION_FAILED] {} {} → 400 on {}",
                request.getMethod(), request.getRequestURI(), fieldErrors.keySet());

        ProblemDetail problem = problem(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultMessage());
        problem.setProperty("fieldErrors", fieldErrors);
        return problem;
    }

    /** A container-element constraint ({@code List<@Valid InviteRequest>}); keyed {@code requests[0].email}. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ProblemDetail handleParameterValidation(HandlerMethodValidationException ex,
                                                   HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            String parameter = pathOf(result);
            if (result instanceof ParameterErrors errors && errors.hasFieldErrors()) {
                errors.getFieldErrors().forEach(error ->
                        fieldErrors.putIfAbsent(parameter + "." + error.getField(), error.getDefaultMessage()));
                continue;
            }
            result.getResolvableErrors().stream().findFirst().ifPresent(error ->
                    fieldErrors.putIfAbsent(parameter, error.getDefaultMessage()));
        }

        log.info("[VALIDATION_FAILED] {} {} → 400 on {}",
                request.getMethod(), request.getRequestURI(), fieldErrors.keySet());

        ProblemDetail problem = problem(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultMessage());
        problem.setProperty("fieldErrors", fieldErrors);
        return problem;
    }

    private static String pathOf(ParameterValidationResult result) {
        String name = result.getMethodParameter().getParameterName();
        String parameter = name == null ? "request" : name;
        return result.getContainerIndex() == null
                ? parameter
                : parameter + "[" + result.getContainerIndex() + "]";
    }

    /**
     * A body that parsed but did not bind (an unknown field, an enum token or date no constant spells, a
     * string where a number goes) names its JSON path, as Bean Validation would; malformed JSON names none.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.debug("Unreadable request body: {}", ex.getMessage());
        if (ex.getCause() instanceof MismatchedInputException mismatch) {
            String field = RequestBodyPaths.of(mismatch);
            if (!field.isEmpty()) {
                String message = mismatch instanceof UnrecognizedPropertyException
                        ? "This request does not take that field"
                        : "Not a valid value";
                log.info("[VALIDATION_FAILED] {} {} → 400 on [{}]", request.getMethod(), request.getRequestURI(), field);
                ProblemDetail problem = problem(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultMessage());
                problem.setProperty("fieldErrors", Map.of(field, message));
                return problem;
            }
        }
        return problem(ErrorCode.VALIDATION_FAILED, "Request body could not be read");
    }

    /** Thrown only where a class is {@code @Validated}; a 500 before this handler existed. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(violation ->
                fieldErrors.putIfAbsent(leafOf(violation.getPropertyPath().toString()), violation.getMessage()));
        log.info("[VALIDATION_FAILED] {} {} → 400 on {}",
                request.getMethod(), request.getRequestURI(), fieldErrors.keySet());
        ProblemDetail problem = problem(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultMessage());
        problem.setProperty("fieldErrors", fieldErrors);
        return problem;
    }

    /** A method-validation path reads {@code method.argument.field}; the caller knows only the field. */
    private static String leafOf(String propertyPath) {
        int firstDot = propertyPath.indexOf('.');
        int secondDot = firstDot < 0 ? -1 : propertyPath.indexOf('.', firstDot + 1);
        return secondDot < 0 ? propertyPath : propertyPath.substring(secondDot + 1);
    }

    /** A truncated verification link ({@code /auth/verify} with no token) shipped as a 500 before this. */
    @ExceptionHandler({ServletRequestBindingException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestPartException.class})
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

    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        log.debug("No resource at {} {}", request.getMethod(), request.getRequestURI());
        return problem(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.defaultMessage());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                  HttpServletRequest request) {
        log.debug("Method {} not supported at {} (supported: {})",
                request.getMethod(), request.getRequestURI(), ex.getSupportedHttpMethods());
        return problem(ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED.defaultMessage());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ProblemDetail handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex,
                                                     HttpServletRequest request) {
        log.debug("Unsupported content type {} at {} {} (supported: {})",
                ex.getContentType(), request.getMethod(), request.getRequestURI(), ex.getSupportedMediaTypes());
        return problem(ErrorCode.UNSUPPORTED_MEDIA_TYPE, ErrorCode.UNSUPPORTED_MEDIA_TYPE.defaultMessage());
    }

    /** Thrown before the controller runs, so no endpoint can answer it. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleUploadTooLarge(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        log.debug("Upload exceeded the multipart limit at {} {}: {}",
                request.getMethod(), request.getRequestURI(), ex.getMessage());
        return problem(ErrorCode.FILE_TOO_LARGE, ErrorCode.FILE_TOO_LARGE.defaultMessage());
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ProblemDetail handleMediaTypeNotAcceptable(HttpMediaTypeNotAcceptableException ex,
                                                      HttpServletRequest request) {
        log.debug("Unacceptable Accept header at {} {} (we produce: {})",
                request.getMethod(), request.getRequestURI(), ex.getSupportedMediaTypes());
        return problem(ErrorCode.NOT_ACCEPTABLE, ErrorCode.NOT_ACCEPTABLE.defaultMessage());
    }

    /** A constraint beat a service pre-check (a race): mapped to the pre-check's code, so 409 not 500. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        // A value the request validation let through and the column refused is the caller's input, not a conflict.
        if (REFUSED_VALUE_STATES.contains(sqlStateOf(ex))) {
            log.warn("[VALIDATION_FAILED] the database refused a value at {} {}: {}",
                    request.getMethod(), request.getRequestURI(), constraintNameOf(ex));
            return problem(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultMessage());
        }
        ErrorCode code = switch (constraintNameOf(ex)) {
            case "app_lm_client_workspace_name_uk" -> ErrorCode.CLIENT_ALREADY_EXISTS;
            case "app_lm_project_triage_company_manual_name_uk" -> ErrorCode.TRIAGE_COMPANY_ALREADY_HELD;
            case "app_lm_person_profile_slug_uk" -> ErrorCode.PERSON_PROFILE_HELD;
            case "app_lm_workspace_candidate_tag_label_uk" -> ErrorCode.CANDIDATE_TAG_EXISTS;
            case "app_lm_workspace_candidate_stage_label_uk", "app_lm_workspace_candidate_stage_key_uk",
                 "app_lm_workspace_candidate_status_label_uk", "app_lm_workspace_candidate_status_key_uk" ->
                    ErrorCode.PIPELINE_LABEL_TAKEN;
            // Two presses adding one person to one position: the second finds them already there.
            case "app_lm_project_candidate_person_uk" -> ErrorCode.CANDIDATE_ALREADY_MAPPED;
            // Two Starts racing one person onto a position: one live sequence per person per position.
            case "app_lm_outreach_enrollment_live_uk" -> ErrorCode.OUTREACH_ALREADY_ENROLLED;
            case "app_lm_strategy_search_shared_name_uk", "app_lm_strategy_search_private_name_uk" ->
                    ErrorCode.STRATEGY_SEARCH_NAME_TAKEN;
            // The key is slugged from the label, so a race loses on whichever index is reached first.
            case "app_lm_project_custom_column_label_uk", "app_lm_project_custom_column_key_uk" ->
                    ErrorCode.CUSTOM_COLUMN_NAME_TAKEN;
            default -> ErrorCode.CONFLICT;
        };
        log.info("[{}] constraint violation at {} {}", code, request.getMethod(), request.getRequestURI());
        return problem(code, code.defaultMessage());
    }

    /** Two writes raced one row's {@code @Version}: expected (two tabs, two teammates), so 409 at info. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException ex,
                                              HttpServletRequest request) {
        log.info("[{}] optimistic lock conflict at {} {}",
                ErrorCode.CONFLICT, request.getMethod(), request.getRequestURI());
        return problem(ErrorCode.CONFLICT, ErrorCode.CONFLICT.defaultMessage());
    }

    private static String sqlStateOf(DataIntegrityViolationException ex) {
        for (Throwable cause = ex.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && sql.getSQLState() != null) {
                return sql.getSQLState();
            }
        }
        return "";
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
     * The catch-all: a bug whose message must be assumed unsafe. A client that hung up is routine (the SPA
     * cancels superseded reads) and logs at debug.
     *
     * @return {@code null} when the caller is gone and the response already committed
     */
    @ExceptionHandler(Exception.class)
    public @Nullable ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        if (ClientDisconnects.isDisconnect(ex)) {
            ClientDisconnects.markGone(request);
            log.debug("Client hung up during {} {}", request.getMethod(), request.getRequestURI());
            // A body here threw again, once per closed tab: no converter writes ProblemDetail as text/event-stream.
            return null;
        }
        log.error("Unhandled exception at {} {}", request.getMethod(), request.getRequestURI(), ex);
        return problem(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage());
    }

    private ProblemDetail problem(ErrorCode code, String detail) {
        return Problems.of(code, detail);
    }
}
