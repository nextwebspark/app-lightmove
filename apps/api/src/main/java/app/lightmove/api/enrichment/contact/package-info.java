/**
 * <b>Contact lookup — the half of enrichment somebody asks for.</b> A researcher presses Find email or
 * Find phone on an executive already mapped, and the provider is asked for that one channel.
 *
 * <p>It answers <b>on the request thread</b>, unlike {@code enrichment.candidate}'s post-commit
 * research: the call is an indexed lookup rather than a scrape, and a press that costs money must be
 * able to report its own failure — a stream event carries a kind and nothing else, so it could not say
 * "no credits left".
 *
 * <p><b>It never writes</b>, which is the package's standing rule: the answer goes back through
 * {@code CandidateService.applyFoundEmails} / {@code applyFoundPhones}, each opening its own
 * transaction.
 *
 * <p>{@code lightmove.enrichment.contactout} sits <b>beside</b> {@code lightmove.enrichment.provider}
 * and is never selected by it. Contact lookup is its own account with its own bill, so turning profile
 * research off leaves these buttons working.
 */
package app.lightmove.api.enrichment.contact;
