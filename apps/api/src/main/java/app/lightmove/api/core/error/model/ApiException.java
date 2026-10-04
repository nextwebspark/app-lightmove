package app.lightmove.api.core.error.model;
import app.lightmove.api.core.error.constant.ErrorCode;

import java.util.Map;
import lombok.Getter;

/**
 * A failure the API means to report; anything else thrown is a bug and answers an opaque 500.
 *
 * <p><b>Two message channels.</b> The constructors' detail reaches the log, never the response;
 * {@link #userFacing} and {@link #withField} opt a fixed sentence into the body. Never interpolate
 * request input there — a server-derived value such as a configured limit may travel.
 */
@Getter
public class ApiException extends RuntimeException {

    private final ErrorCode code;

    /** The sentence to show the caller, or null to use {@link ErrorCode#defaultMessage()}. */
    private final String clientDetail;

    /** Field → message, in Bean Validation's shape. */
    private final Map<String, String> fieldErrors;

    /** Extra members of the problem body, server-derived only — ids the caller may act on next. */
    private final Map<String, Object> properties;

    public ApiException(ErrorCode code) {
        this(code, code.defaultMessage(), null, null, null);
    }

    /** @param internalDetail for the log and audit trail, never returned to the client */
    public ApiException(ErrorCode code, String internalDetail) {
        this(code, internalDetail, null, null, null);
    }

    private ApiException(ErrorCode code, String internalDetail, String clientDetail,
                         Map<String, String> fieldErrors, Map<String, Object> properties) {
        super(internalDetail);
        this.code = code;
        this.clientDetail = clientDetail;
        this.fieldErrors = fieldErrors;
        this.properties = properties;
    }

    public static ApiException of(ErrorCode code) {
        return new ApiException(code);
    }

    /** @param message a fixed sentence, never request input; replaces the code's default wording */
    public static ApiException userFacing(ErrorCode code, String message) {
        return new ApiException(code, message, message, null, null);
    }

    /** {@link #userFacing}, attributed to one input in the {@code fieldErrors} shape. */
    public static ApiException withField(ErrorCode code, String field, String message) {
        return new ApiException(code, message, message, Map.of(field, message), null);
    }

    public static ApiException withFields(ErrorCode code, Map<String, String> fieldErrors) {
        String summary = String.join("; ", fieldErrors.values());
        return new ApiException(code, summary, code.defaultMessage(), Map.copyOf(fieldErrors), null);
    }

    /** The code's own sentence, plus one member the caller needs to answer it. Never request input. */
    public static ApiException withProperty(ErrorCode code, String name, Object value) {
        return new ApiException(code, code.defaultMessage(), null, null, Map.of(name, value));
    }
}
