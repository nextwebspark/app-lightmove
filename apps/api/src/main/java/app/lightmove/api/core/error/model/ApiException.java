package app.lightmove.api.core.error.model;
import app.lightmove.api.core.error.constant.ErrorCode;

import lombok.Getter;

/**
 * A failure the API means to report. Carries an {@link ErrorCode}, which decides both the HTTP status
 * and the message the client sees.
 *
 * <p>Anything thrown that is <i>not</i> one of these is, by definition, a bug — {@code
 * GlobalExceptionHandler} logs those with a stack trace and returns an opaque 500, because an
 * unplanned exception's message is as likely to contain a table name as anything useful.
 */
@Getter
public class ApiException extends RuntimeException {

    private final ErrorCode code;

    public ApiException(ErrorCode code) {
        super(code.defaultMessage());
        this.code = code;
    }

    /**
     * @param internalDetail context for the log and the audit trail — never returned to the client.
     */
    public ApiException(ErrorCode code, String internalDetail) {
        super(internalDetail);
        this.code = code;
    }

    public static ApiException of(ErrorCode code) {
        return new ApiException(code);
    }
}
