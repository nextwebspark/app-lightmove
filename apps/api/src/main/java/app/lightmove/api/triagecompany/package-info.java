/**
 * <b>Triage company — the mandate's own shortlist of the market.</b> One thing only: the mapping of a
 * project to a company it took out of the market, and the stage that pairing has reached —
 * {@link app.lightmove.api.triagecompany.constant.TriageCompanyStatus#IN_UNIVERSE In universe},
 * {@link app.lightmove.api.triagecompany.constant.TriageCompanyStatus#SHORTLISTED Shortlisted},
 * {@link app.lightmove.api.triagecompany.constant.TriageCompanyStatus#DECLINED Declined}.
 *
 * <p>The order is the whole story: a consultant filters the market in {@code strategy}, then moves the
 * companies worth keeping into this package, where they are persisted per project and triaged. A
 * strategy company is a row of the market that belongs to nobody; a triage company is a decision.
 *
 * <p>Every row is a <b>write-time snapshot</b> resolved from the market rather than a foreign key:
 * the Apollo pipeline reloads its table wholesale, so a mandate that recorded a decision must keep
 * rendering the company it decided about even after the export stops publishing it.
 *
 * <p><b>What is deliberately not here:</b> searching, filtering, bands, facets, the sector taxonomy —
 * none of that describes a project's relationship to a company, so all of it lives in
 * {@code strategy} (see {@link app.lightmove.api.strategy}). This package depends on strategy for the
 * one thing it cannot do alone — resolving a company id, and resolving a mandate's saved filter for
 * "Add all to Universe" — and strategy never depends back.
 */
package app.lightmove.api.triagecompany;
