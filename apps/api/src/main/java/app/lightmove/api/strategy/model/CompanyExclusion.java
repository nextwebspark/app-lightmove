package app.lightmove.api.strategy.model;

import java.util.Map;

/**
 * An opaque SQL predicate, with its parameters, ANDed into a {@link CompanyScope} by a caller outside
 * {@code strategy} — so the search never names {@code triagecompany}'s table. It may reference the
 * universe row under the alias {@code a}.
 */
public record CompanyExclusion(String sql, Map<String, Object> params) {

    /** Every caller but the Strategy search itself. */
    public static final CompanyExclusion NONE = new CompanyExclusion(null, Map.of());

    public boolean isPresent() {
        return sql != null;
    }
}
