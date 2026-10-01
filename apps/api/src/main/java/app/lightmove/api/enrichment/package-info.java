/**
 * <b>Enrichment</b> — vendor research on what a capture could only point at, run after the commit and
 * off the request thread ({@code contact} answers inline). It never writes a mandate's row: answers go
 * back through the owning feature's public method, and every vendor call goes through
 * {@link app.lightmove.api.core.resilience}. Its vendor caches — {@code app_lm_vendor_company} (V64),
 * {@code app_lm_vendor_person} and {@code app_lm_vendor_people_search} (V87) — carry no workspace,
 * project or user — a tenant boundary — and never hold a typed row;
 * {@code app_lm_executive_sourcing_run} (V86) is one mandate's Find executives run, kept so the screen
 * can poll it. Types the owning features consume live with them, so the dependency stays one-way;
 * {@code sourcing} and {@code peoplesearch} are the halves that write a mandate's rows, and they do so
 * through {@code CandidateService.addResearched} alone, the employer through
 * {@code TriageCompanyService.captureFromResearch}.
 */
package app.lightmove.api.enrichment;
