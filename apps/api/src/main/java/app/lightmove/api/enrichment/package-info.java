/**
 * <b>Enrichment — researching what the plugin could only point at.</b> A capture arrives with a name
 * and a LinkedIn URL; this package calls the vendor off the request thread and after the commit.
 * Split by subject ({@code candidate} / {@code company} / {@code common}), then by type.
 *
 * <p><b>It never writes.</b> The answer goes back through one public method on the owning feature —
 * {@code CandidateService.applyResearch}, {@code TriageCompanyService.applyEnrichment} — which opens
 * its own transaction. Every vendor call goes through {@link app.lightmove.api.core.resilience}.
 *
 * <p>The dependency runs one way, which is why types the owning features consume live with them
 * rather than here: {@code EnrichedProfile} and {@code EnrichedPhoto} are {@code candidate}'s
 * contract for what it takes from research, the captured events belong to whoever publishes them, and
 * {@code CapturedCompanyDetails} is the hand-typed capture path's record too. Any of them moved here
 * turns that line into a cycle.
 */
package app.lightmove.api.enrichment;
