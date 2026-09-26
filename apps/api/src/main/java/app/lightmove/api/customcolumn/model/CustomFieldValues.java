package app.lightmove.api.customcolumn.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A row's {@code custom_fields} jsonb, keyed by {@link ProjectCustomColumn#getFieldKey()}. Values
 * are stored as entered, so a type change loses nothing; {@link JsonValue} and {@link JsonCreator}
 * keep the document flat.
 */
public final class CustomFieldValues {

    private static final CustomFieldValues EMPTY = new CustomFieldValues(Map.of());

    private final Map<String, String> values;

    private CustomFieldValues(Map<String, String> values) {
        this.values = values;
    }

    /**
     * The one place a document is normalised. A blank key or a null value is dropped: an entry
     * nothing can be looked up by is not data, and a null would have every reader guarding for it.
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
