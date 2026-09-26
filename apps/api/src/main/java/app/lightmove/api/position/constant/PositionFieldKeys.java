package app.lightmove.api.position.constant;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The scalars {@code Position.fieldSources} may name, one allow-list per step — the only validation a
 * {@code fieldSources} key gets.
 */
public final class PositionFieldKeys {

    public static final Set<String> DETAILS =
            Set.of("department", "locationCity", "locationCountry", "employmentType", "seniority", "narrative");

    public static final Set<String> CONTEXT = Set.of("mandateReason", "businessDriver");

    public static final Set<String> REPORTING = Set.of("teamSize", "noticeValue", "noticeUnit");

    private PositionFieldKeys() {
    }

    public static void requireKnown(Map<String, FieldSource> fieldSources, Set<String> allowed) {
        for (String key : fieldSources.keySet()) {
            if (!allowed.contains(key)) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown field key: " + key);
            }
        }
    }

    public static Map<String, FieldSource> allManual(Set<String> keys) {
        Map<String, FieldSource> manual = new LinkedHashMap<>();
        keys.forEach(key -> manual.put(key, FieldSource.MANUAL));
        return manual;
    }
}
