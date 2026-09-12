/**
 * <b>Triage company — a mandate's decision about a company.</b> One project-to-company row per
 * decision, carrying the stage it has reached and a write-time snapshot of the company rather than a
 * foreign key: the Apollo pipeline reloads its table wholesale, and
 * {@code app_lm_apollo_companies} is ETL-owned and read-only here. Removing a row removes this
 * mandate's decision and nothing else.
 *
 * <p>Depends on {@code strategy} for resolving a company id and a saved filter; strategy never
 * depends back.
 */
package app.lightmove.api.triagecompany;
