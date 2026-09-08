/**
 * <b>Candidate — the people half of a talent map.</b> One row per executive a mandate has mapped, with
 * the profile a consultant works from: where they sit, how to reach them, what they are paid, and
 * where this mandate's research has got to with them.
 *
 * <p><b>The project is the mapping; the company is optional.</b> A candidate belongs to the mandate
 * they were researched for, because the note, the status and the compensation reading are all
 * mandate-specific — the same person researched for two mandates is two rows. Most are found at a
 * company already in the mandate's universe and carry that company's triage row.
 *
 * <p><b>Optional is not the same as absent, and the difference is the whole shape of the Companies
 * grid.</b> An employer somebody <i>names</i> — typed into the drawer, read out of a spreadsheet,
 * answered by a vendor's research — is filed into the mandate's universe and the person is mapped to
 * it, so the grid draws a company line rather than a person floating beside one. What stays unmapped
 * is a row with no employer named at all: a plugin capture before its research lands, a sheet of bare
 * names. Refusing those is what pushes a name into a spreadsheet, which is what these screens replace.
 *
 * <p>The employer is a <b>write-time snapshot</b> beside the mapping, for the reason
 * {@link app.lightmove.api.triagecompany} snapshots a company: it has to outlive the row it was copied
 * from. What no longer happens is the mapping being dropped under a person —
 * {@link app.lightmove.api.candidate.service.CompanyRemovalGuard} refuses to remove a company any
 * executive is mapped at, and V36's {@code ON DELETE SET NULL} is the floor beneath it.
 *
 * <p><b>What is deliberately not here:</b> the market. This package never reads
 * {@code app_lm_apollo_companies} and never searches anything — it depends on
 * {@link app.lightmove.api.triagecompany} through two public methods — resolving the mandate's own
 * company row a candidate is being mapped to, and filing a named employer into the universe — and
 * triagecompany never depends back. That direction is what keeps the Companies grid's company list
 * free of any knowledge of people.
 *
 * <p>{@link app.lightmove.api.candidate.constant.CandidateSource} is the door a profile came through,
 * and it decides the door its employer's company row is badged with — a name typed on a person is a
 * manual company, one a vendor answered with is an extension company.
 */
package app.lightmove.api.candidate;
