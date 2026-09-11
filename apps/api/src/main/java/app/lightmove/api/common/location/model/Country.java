package app.lightmove.api.common.location.model;

import java.util.List;

/**
 * A country as this application spells it: the ISO 3166-1 alpha-2 code and the one English name every
 * screen shows. Two rows are the same country when their codes match.
 */
public record Country(String code, String name, List<String> spellings) {

    public Country {
        spellings = spellings == null ? List.of() : List.copyOf(spellings);
    }

    public Country(String code, String name) {
        this(code, name, List.of());
    }
}
