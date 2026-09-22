package app.lightmove.api.position.constant;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The eleven scalars {@code Position.fieldSources} may name, one allow-list per wizard step. Nothing else
 * validates a {@code fieldSources} key, so without this gate a caller could stamp provenance on a wire
 * name that means nothing — the compensation figures and the role title are deliberately not here,
 * because neither is ever auto-filled.
 */
public final class PositionFieldKeys {

    public static final Set<String> DETAILS =
            Set.of("department", "locationCity", "locationCountry", "employmentType", "seniority", "narrative");

    public static final Set<String> CONTEXT = Set.of("mandateReason", "businessDriver");

    public static final Set<String> REPORTING = Set.of("teamSize", "noticeValue", "noticeUnit");

    private PositionFieldKeys() {
    }

    /** Refuses a {@code fieldSources} map naming anything outside this step's allow-list. */
    public static void requireKnown(Map<String, FieldSource> fieldSources, Set<String> allowed) {
        for (String key : fieldSources.keySet()) {
            if (!allowed.contains(key)) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown field key: " + key);
            }
        }
    }

    /**
     * Every key of a step stamped {@code MANUAL} — the caller sent no provenance for this step at
     * all, so the conservative reading is that a person typed the whole thing.
     */
    public static Map<String, FieldSource> allManual(Set<String> keys) {
        Map<String, FieldSource> manual = new LinkedHashMap<>();
        keys.forEach(key -> manual.put(key, FieldSource.MANUAL));
        return manual;
    }
}
