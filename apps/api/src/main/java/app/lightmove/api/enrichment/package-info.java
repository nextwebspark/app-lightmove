/**
 * <b>Enrichment — researching what the plugin could only point at.</b> A capture arrives with a name
 * and a LinkedIn URL; this package turns that into the profile or the company facts a consultant
 * would otherwise type, off the capture's request thread and after its commit.
 *
 * <p>Split first by what is researched — {@code candidate} for people, {@code company} for their
 * employers, {@code common} for the little both Bright Data lookups share — and then by type inside
 * each, the same {@code service} / {@code config} / {@code model} layout every other feature uses.
 * The two halves are near-identical in shape on purpose: an adapter, a port, an {@code @Async} worker
 * and the {@code @Bean} config that picks between providers.
 *
 * <p><b>This package never writes.</b> It calls the vendor and hands the answer back through one
 * public method on the owning feature — {@code CandidateService.applyResearch} and
 * {@code TriageCompanyService.applyEnrichment} — which opens its own transaction and announces the
 * change on the project stream. A package that reached into {@code candidate}'s and
 * {@code triagecompany}'s repositories would be a third feature with write access to two aggregates,
 * and the write must not share a thread with the HTTP call anyway: a retry's backoff would hold a
 * database connection for seconds.
 *
 * <p><b>What deliberately stays outside.</b> The dependency runs one way — enrichment depends on
 * {@code candidate} and {@code triagecompany}, never the reverse — so a type either of them consumes
 * cannot live here without turning that line into a cycle:
 * <ul>
 *   <li>{@code EnrichedProfile} and {@code EnrichedPhoto} are what {@code Candidate.enrich} and
 *       {@code CandidatePhoto.of} accept. They are the aggregate's contract for "here is what I take
 *       from research", stated in its own vocabulary so the entity never learns which vendor
 *       answered — which is why they belong to {@code candidate.model} despite existing only for
 *       this package's sake.</li>
 *   <li>{@code CandidateCapturedEvent} and {@code TriageCompanyCapturedEvent} belong to whoever
 *       publishes them, the way {@code EmailVerifiedEvent} does: the announcement is the aggregate's,
 *       and the listener is the one that depends.</li>
 *   <li>{@code CapturedCompanyDetails} is the plain capture path's record too — a consultant typing a
 *       company in fills the same fields — so it is shared rather than enrichment's.</li>
 * </ul>
 *
 * <p>Every vendor call goes through {@link app.lightmove.api.core.resilience}, so no adapter here
 * decides its own timeouts, retries or failure handling.
 */
package app.lightmove.api.enrichment;
