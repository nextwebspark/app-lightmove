package app.lightmove.api.company.service;

import java.util.Map;

/** A rendered SQL predicate and its named parameters — {@code CompanyQueryService}'s builder output. */
record WhereClause(String sql, Map<String, Object> params) {}
