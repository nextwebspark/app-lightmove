package app.lightmove.api.strategy.model;

import java.util.Map;

/**
 * An opaque narrowing of a {@link CompanyScope} that a caller outside {@code strategy} supplies — a
 * boolean SQL predicate over {@code app_lm_apollo_companies}, plus its own bind parameters, ANDed
 * into the scope's WHERE clause without {@code strategy} knowing what it tests.
 *
 * <p>This is the seam {@code TriagedCompanyLookup} hands its answer through: {@code triagecompany}
 * builds the predicate against its own table and {@code strategy} only composes it, so the search
 * never needs {@code triagecompany}'s table name in its own SQL to keep a company excluded once it is
 * triaged. See {@code TriagedCompanyLookupAdapter} for the one predicate this codebase builds today.
 *
 * <p>{@code sql} may reference the row under the alias {@code a} — {@code ApolloCompanyQueryService}
 * always queries {@code app_lm_apollo_companies} under that alias precisely so a correlated predicate
 * like a {@code NOT EXISTS} has something unambiguous to join back to.
 */
public record CompanyExclusion(String sql, Map<String, Object> params) {

    /** No further narrowing — every caller but the Strategy search itself. */
    public static final CompanyExclusion NONE = new CompanyExclusion(null, Map.of());

    public boolean isPresent() {
        return sql != null;
    }
}
