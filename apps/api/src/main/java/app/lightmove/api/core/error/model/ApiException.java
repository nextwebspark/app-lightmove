package app.lightmove.api.core.error.model;
import app.lightmove.api.core.error.constant.ErrorCode;

import java.util.Map;
import lombok.Getter;

/**
 * A failure the API means to report, carrying an {@link ErrorCode} that decides both the HTTP status
 * and the message the client sees. Anything thrown that is <i>not</i> one of these is a bug, and
 * {@code GlobalExceptionHandler} answers it with an opaque 500.
 *
 * <p><b>Two message channels.</b> The ordinary constructors take an <i>internal</i> detail that
 * reaches the log and never the response, so a rule may quote a column or a rejected value freely.
 * {@link #userFacing} and {@link #withField} opt a fixed sentence into the body.
 *
 * <p>Never interpolate <b>request input</b> into {@code userFacing} — a message that reflects what the
 * caller sent is an echo, which is what the default channel exists to suppress. A value the server
 * derived itself, such as a configured limit, is not input and may travel.
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
     * <p>For rules the API knows and the client cannot infer. The same text is logged.
     *
     * @param message a fixed sentence. Never request input; see the class doc.
     */
    public static ApiException userFacing(ErrorCode code, String message) {
        return new ApiException(code, message, message, null);
    }

    /**
     * The same, attributed to one input, in the {@code fieldErrors} shape Bean Validation produces —
     * so a service-level rule reaches a form the way a {@code @Size} does.
     */
    public static ApiException withField(ErrorCode code, String field, String message) {
        return new ApiException(code, message, message, Map.of(field, message));
    }
}
