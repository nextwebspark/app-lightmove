package app.lightmove.api.strategy.service;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;

/**
 * How a read over the company universe treats the {@code limit} and the query text a caller sends.
 *
 * <p>Lifted out of {@code CompanySearchController} when AI Research became a second caller wanting
 * the same rule and the same wording. The rule is the interesting half: a limit past the ceiling is
 * <b>refused, not clamped</b>, because a silently narrowed limit is a wrong answer the caller cannot
 * tell it got.
 */
public final class CompanySearchLimits {

    private CompanySearchLimits() {
    }

    /** The caller's limit, the configured default when it names none, or a refusal. */
    public static int resolved(Integer limit, int fallback, int ceiling) {
        if (limit == null) {
            return fallback;
        }
        if (limit < 1 || limit > ceiling) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "limit must be between 1 and " + ceiling);
        }
        return limit;
    }

    /** The trimmed text, or a refusal past the ceiling. A scope, not an attack. */
    public static String accepted(String field, String text, int maxLength) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.length() > maxLength) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    field + " exceeds " + maxLength + " characters");
        }
        return trimmed;
    }
}
