package app.lightmove.api.common.constant;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;

/** An enum the API spells by a stable token rather than by its constant name. */
public interface ApiValueEnum {

    String value();

    /** The constant spelled {@code token}, or null when none is. */
    static <E extends Enum<E> & ApiValueEnum> E fromValue(Class<E> type, String token) {
        return token == null ? null : type.cast(ApiValueTokenIndex.lookup(type, token));
    }

    /** {@code whenBlank} for an absent token, otherwise as {@link #require}. */
    static <E extends Enum<E> & ApiValueEnum> E parse(Class<E> type, String token, E whenBlank, String label) {
        return token == null || token.isBlank() ? whenBlank : require(type, token, label);
    }

    /** The constant spelled {@code token}; a 400 for any token no constant spells, absent included. */
    static <E extends Enum<E> & ApiValueEnum> E require(Class<E> type, String token, String label) {
        E resolved = fromValue(type, token);
        if (resolved == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown " + label + ": " + token);
        }
        return resolved;
    }
}
