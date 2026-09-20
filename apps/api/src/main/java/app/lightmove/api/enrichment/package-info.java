/**
 * <b>Enrichment — researching what the plugin could only point at.</b> A capture arrives with a name
 * and a LinkedIn URL; this package calls the vendor for what it could not carry. Split by subject
 * ({@code candidate} / {@code company} / {@code contact} / {@code common}), then by type.
 *
 * <p>Research runs after the commit and off the request thread. {@code contact} is the other shape —
 * somebody presses a button and waits — and its own package doc says why it answers inline.
 *
 * <p><b>It never writes.</b> The answer goes back through one public method on the owning feature —
 * {@code CandidateService.applyResearch}, {@code TriageCompanyService.applyEnrichment} — which opens
 * its own transaction. Every vendor call goes through {@link app.lightmove.api.core.resilience}.
 *
 * <p><b>One exception, and it owns no mandate's row.</b> {@code company} keeps {@code app_lm_company}
 * (V64) — what a provider said about a LinkedIn page, remembered so the same company is not bought
 * once per mandate. It carries no workspace, project or user, for the reason
 * {@code app_lm_geocoded_place} carries none, and a row a person typed never reaches it.
 *
 * <p>The dependency runs one way, which is why types the owning features consume live with them
 * rather than here: {@code EnrichedProfile} and {@code EnrichedPhoto} are {@code candidate}'s
 * contract for what it takes from research, the captured events belong to whoever publishes them, and
 * {@code CapturedCompanyDetails} is the hand-typed capture path's record too. Any of them moved here
 * turns that line into a cycle. {@code VendorCompanyRecord} is the other direction and belongs here:
 * it is what a provider said in its own words, and nothing outside this package reads it.
 */
package app.lightmove.api.enrichment;
