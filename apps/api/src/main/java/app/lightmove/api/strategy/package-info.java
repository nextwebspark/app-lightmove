/**
 * <b>Strategy — the market side.</b> Everything about finding companies: the filter a mandate saves,
 * the searches saved beside it, and the reads over the company universe
 * ({@code app_lm_apollo_companies}) that turn a filter into a list.
 *
 * <p>The universe is ETL-owned reference data shared by the whole product — 71,822 GCC companies
 * keyed on {@code apollo_account_id}, read-only to the application. So the vocabulary a search is
 * expressed in lives here too: {@link app.lightmove.api.strategy.constant.EmployeeBand},
 * {@link app.lightmove.api.strategy.constant.RevenueBand}, the sector taxonomy, the market segments,
 * the facet counts and the sort allowlist. A band is a way of asking the market a question, not a
 * property of any one mandate, which is why none of it sits in {@code project}.
 *
 * <p><b>What is deliberately not here:</b> the decision a mandate makes about a company it found.
 * That is a project-to-company row with a triage status and lives in {@code company} — see
 * {@link app.lightmove.api.triagecompany}. Strategy answers "which companies match?"; company answers
 * "what did this mandate do about that one?". The dependency runs one way: {@code company} reads
 * strategy's scope to bulk-add from it, and strategy never looks at a mandate's universe.
 */
package app.lightmove.api.strategy;
