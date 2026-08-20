package app.lightmove.api.strategy.service;

import java.util.Map;

/** A rendered SQL predicate and its named parameters — the WHERE-clause builder's output. */
record WhereClause(String sql, Map<String, Object> params) {}
