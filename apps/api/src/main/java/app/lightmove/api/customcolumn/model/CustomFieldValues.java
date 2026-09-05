package app.lightmove.api.customcolumn.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The values a row holds for its project's custom columns — the {@code custom_fields} jsonb column on
 * both {@code app_lm_project_triage_company} and {@code app_lm_project_candidate}.
 *
 * <p>Keyed by {@link ProjectCustomColumn#getFieldKey()}, and every value is the <b>string it was
 * entered as</b>. The column's declared type decides what is accepted on the way in, never how it is
 * kept: a mandate that imports a file, then corrects a column from TEXT to NUMBER, must not lose the
 * rows it already filled in. Nothing sorts or filters on these server-side, so storing "2019" as text
 * costs nothing.
 *
 * <p>An open map rather than a record of named fields, because the field names belong to the project
 * and are not knowable at compile time. That is also why it cannot share the candidate's existing
 * {@code profile} column: {@link app.lightmove.api.candidate.model.CandidateProfile} is a typed record
 * read by field, and it would have to preserve keys it knows nothing about.
 *
 * <p>{@link JsonValue} and {@link JsonCreator} are what keep the stored document flat —
 * {@code {"ethnicity":"Emirati"}} rather than a map nested under a wrapper key — so the column reads
 * the way a person querying the database would expect. Unknown keys need no special tolerance here:
 * a bag whose keys are the project's own reads back whatever was written, and a value left behind by a
 * retired column is simply never rendered.
 */
public final class CustomFieldValues {

    private static final CustomFieldValues EMPTY = new CustomFieldValues(Map.of());

    private final Map<String, String> values;

    private CustomFieldValues(Map<String, String> values) {
        this.values = values;
    }

    /**
     * The one way in, and the one place a document is normalised. A blank key or a null value is
     * dropped rather than kept: an entry nothing can be looked up by is not data, and a null would
     * have every reader guarding for it.
     */
    @JsonCreator
    public static CustomFieldValues of(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return EMPTY;
        }
        Map<String, String> cleaned = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (key != null && !key.isBlank() && value != null) {
                cleaned.put(key, value);
            }
        });
        return cleaned.isEmpty() ? EMPTY : new CustomFieldValues(Map.copyOf(cleaned));
    }

    /** What a row reads as before anyone has filled a custom column in. */
    public static CustomFieldValues empty() {
        return EMPTY;
    }

    /** The bag itself — what Jackson writes to the column, and what the API hands back. */
    @JsonValue
    public Map<String, String> asMap() {
        return values;
    }

    public Optional<String> get(String fieldKey) {
        return Optional.ofNullable(values.get(fieldKey));
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CustomFieldValues that && values.equals(that.values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }

    @Override
    public String toString() {
        return "CustomFieldValues" + values;
    }
}
