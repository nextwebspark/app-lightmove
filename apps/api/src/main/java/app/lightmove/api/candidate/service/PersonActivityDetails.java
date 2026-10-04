package app.lightmove.api.candidate.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What a timeline line says beyond its kind. A null value is left out, so a detail that does not apply
 * to this change needs no branch at the call site; enums and ids are kept as their names.
 */
final class PersonActivityDetails {

    private final Map<String, Object> values = new LinkedHashMap<>();

    private PersonActivityDetails() {
    }

    static PersonActivityDetails none() {
        return new PersonActivityDetails();
    }

    static PersonActivityDetails of(String key, Object value) {
        return none().and(key, value);
    }

    PersonActivityDetails and(String key, Object value) {
        if (value instanceof Enum<?> constant) {
            values.put(key, constant.name());
        } else if (value instanceof UUID id) {
            values.put(key, id.toString());
        } else if (value != null) {
            values.put(key, value);
        }
        return this;
    }

    Map<String, Object> asMap() {
        return values;
    }
}
