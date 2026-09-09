/**
 * <b>Candidate — the people half of a talent map.</b> One row per executive a mandate has mapped. The
 * project is the mapping and the triage company is optional, because a researcher meets people at
 * companies the universe does not carry; the employer is snapshotted beside it so removing a company
 * from a mandate unmaps its executives rather than deleting them.
 *
 * <p>Depends on {@code triagecompany} to resolve the company a person is mapped to, and never reads
 * the market itself; triagecompany never depends back.
 */
package app.lightmove.api.candidate;
