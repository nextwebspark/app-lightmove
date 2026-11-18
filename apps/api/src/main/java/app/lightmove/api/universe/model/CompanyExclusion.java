package app.lightmove.api.universe.model;

import java.util.Map;
import org.springframework.modulith.NamedInterface;

/**
 * An opaque SQL predicate, with its parameters, ANDed into a {@link CompanyScope} by a caller outside
 * {@code strategy} — so the search never names {@code triagecompany}'s table. It may reference the
 * universe row under the alias {@code a}.
 */
@NamedInterface(name = "api", propagate = false)
public record CompanyExclusion(String sql, Map<String, Object> params) {

    /** Every caller but the Strategy search itself. */
    public static final CompanyExclusion NONE = new CompanyExclusion(null, Map.of());

    public boolean isPresent() {
        return sql != null;
    }
}
