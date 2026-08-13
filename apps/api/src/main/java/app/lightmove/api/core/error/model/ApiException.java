package app.lightmove.api.core.error.model;
import app.lightmove.api.core.error.constant.ErrorCode;

import java.util.Map;
import lombok.Getter;

/**
 * A failure the API means to report. Carries an {@link ErrorCode}, which decides both the HTTP status
 * and the message the client sees.
 *
 * <p>Anything thrown that is <i>not</i> one of these is, by definition, a bug — {@code
 * GlobalExceptionHandler} logs those with a stack trace and returns an opaque 500, because an
 * unplanned exception's message is as likely to contain a table name as anything useful.
 *
 * <p><b>Two message channels, and the difference matters.</b> The ordinary constructors take an
 * <i>internal</i> detail: it reaches the log and the audit trail and never the response, so a rule
 * that names a column, a constraint or a rejected value can say so freely. {@link #userFacing} and
 * {@link #withField} are the opt-in for the other kind — a fixed sentence written to be read by the
 * person who made the request, which the handler renders as the {@code detail}.
 *
 * <p>Only ever hand {@code userFacing} a literal. The moment a message interpolates request input it
 * becomes a reflection of whatever the caller sent, which is precisely what the default channel exists
 * to suppress; those stay on the plain constructor and answer with the code's own wording.
 */
@Getter
public class ApiException extends RuntimeException {

    private final ErrorCode code;

    /** The sentence to show the caller, or null to use {@link ErrorCode#defaultMessage()}. */
    private final String clientDetail;

    /** Field → message, rendered like Bean Validation's so a form can put it under the right input. */
    private final Map<String, String> fieldErrors;

    public ApiException(ErrorCode code) {
        this(code, code.defaultMessage(), null, null);
    }

    /**
     * @param internalDetail context for the log and the audit trail — never returned to the client.
     */
    public ApiException(ErrorCode code, String internalDetail) {
        this(code, internalDetail, null, null);
    }

    private ApiException(ErrorCode code, String internalDetail, String clientDetail,
                         Map<String, String> fieldErrors) {
        super(internalDetail);
        this.code = code;
        this.clientDetail = clientDetail;
        this.fieldErrors = fieldErrors;
    }

    public static ApiException of(ErrorCode code) {
        return new ApiException(code);
    }

    /**
     * A refusal whose reason the caller is meant to read, replacing the code's default wording.
     *
     * <p>For rules the API knows and the client cannot infer — "clients are invited to a project, not
     * granted through the roster". The same text is logged, so nothing is lost from the trail.
     *
     * @param message a fixed sentence. Never request input; see the class doc.
     */
    public static ApiException userFacing(ErrorCode code, String message) {
        return new ApiException(code, message, message, null);
    }

    /**
     * The same, attributed to one input, so the SPA renders it under that field rather than as a
     * banner. Matches the {@code fieldErrors} shape Bean Validation failures already produce, which is
     * how a service-level rule reaches a form the way a {@code @Size} does.
     */
    public static ApiException withField(ErrorCode code, String field, String message) {
        return new ApiException(code, message, message, Map.of(field, message));
    }
}
