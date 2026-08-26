/**
 * <b>Triage company — the mandate's own shortlist of the market.</b> One thing only: the mapping of a
 * project to a company it has taken a position on, and the stage that pairing has reached —
 * {@link app.lightmove.api.triagecompany.constant.TriageCompanyStatus#IN_UNIVERSE In universe},
 * {@link app.lightmove.api.triagecompany.constant.TriageCompanyStatus#SHORTLISTED Shortlisted},
 * {@link app.lightmove.api.triagecompany.constant.TriageCompanyStatus#DECLINED Declined}.
 *
 * <p>The order is the whole story: a consultant filters the market in {@code strategy}, then moves the
 * companies worth keeping into this package, where they are persisted per project and triaged. A
 * strategy company is a row of the market that belongs to nobody; a triage company is a decision.
 *
 * <p><b>Three doors in, not one.</b> The market is the widest but not the only one — a consultant may
 * type a company in, or capture one off a live page with the browser plugin, and neither has a universe
 * id to be keyed by. {@link app.lightmove.api.triagecompany.constant.TriageCompanySource} records which
 * door a row came through, and it is provenance the grid shows rather than bookkeeping: a headcount
 * exported by Apollo and one read off a careers page are not equally trustworthy.
 *
 * <p>Every row taken from the market is a <b>write-time snapshot</b> rather than a foreign key: the
 * Apollo pipeline reloads its table wholesale, so a mandate that recorded a decision must keep
 * rendering the company it decided about even after the export stops publishing it. The same follows
 * for deletion — removing a row here removes <i>this mandate's decision</i> and nothing else. The
 * company stays in the universe, findable on Strategy, untouched for every other mandate;
 * {@code app_lm_apollo_companies} is ETL-owned and this application holds SELECT on it and no more.
 *
 * <p><b>What is deliberately not here:</b> searching, filtering, bands, facets, the sector taxonomy —
 * none of that describes a project's relationship to a company, so all of it lives in
 * {@code strategy} (see {@link app.lightmove.api.strategy}). This package depends on strategy for the
 * things it cannot do alone — resolving a company id, resolving a mandate's saved filter for
 * "Add all to Universe", and the shared sort-direction vocabulary — and strategy never depends back.
 */
package app.lightmove.api.triagecompany;
