package app.lightmove.api.company.service;

import org.springframework.jdbc.core.simple.JdbcClient;

/** A statement with a parameter map already bound — {@code CompanyQueryService}'s bind result. */
record StatementParams(JdbcClient.StatementSpec spec) {}
