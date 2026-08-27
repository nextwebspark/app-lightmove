/**
 * <b>Candidate — the people half of a talent map.</b> One row per executive a mandate has mapped, with
 * the profile a consultant works from: where they sit, how to reach them, what they are paid, and
 * where this mandate's research has got to with them.
 *
 * <p><b>The project is the mapping; the company is optional.</b> A candidate belongs to the mandate
 * they were researched for, because the note, the status and the compensation reading are all
 * mandate-specific — the same person researched for two mandates is two rows. Most are found at a
 * company already in the mandate's universe and carry that company's triage row; a researcher also
 * meets people at companies the universe does not carry, and those rows carry none rather than being
 * refused.
 *
 * <p>The employer is a <b>write-time snapshot</b> beside the mapping, for the reason
 * {@link app.lightmove.api.triagecompany} snapshots a company: removing a company from a mandate drops
 * the mandate's decision about the company and must not delete the people mapped at it. They fall back
 * to unmapped rows that still say where the person works.
 *
 * <p><b>What is deliberately not here:</b> the market. This package never reads
 * {@code app_lm_apollo_companies} and never searches anything — it depends on
 * {@link app.lightmove.api.triagecompany} through one public method, to resolve the mandate's own
 * company row a candidate is being mapped to, and triagecompany never depends back. That direction is
 * what keeps the Companies grid's company list free of any knowledge of people.
 *
 * <p>Only manual entry is built. {@link app.lightmove.api.candidate.constant.CandidateSource} carries
 * the CSV import and the browser plugin from the start anyway, because provenance nobody recorded
 * cannot be backfilled later.
 */
package app.lightmove.api.candidate;
