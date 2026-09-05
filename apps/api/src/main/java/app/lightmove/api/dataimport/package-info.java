/**
 * <b>Data import — the fourth door into a mandate's universe.</b> A consultant's spreadsheet: read it,
 * work out what its columns mean, and write what it carries through the doors that already exist.
 *
 * <p>Strategy's per-row add, the hand-typed company and the browser plugin all put one row in at a
 * time. A researcher who arrives with a list a client sent, or a longlist bought from a vendor, had no
 * way in at all — which is why {@code app_lm_project_candidate.source} has reserved {@code 'CSV'}
 * since V36 for an import that did not exist.
 *
 * <p><b>This package writes nothing itself.</b> It builds the very requests the Companies drawer posts
 * — a capture, an edit, a saved candidate — and hands them to {@code triagecompany} and
 * {@code candidate}. Every scope check, duplicate rule, snapshot and audit event stays in the one
 * place that already owns it, and an import cannot drift from what the screen does. It depends on
 * those two and on {@code customcolumn}; none of them depends back.
 *
 * <p><b>Two problems, and the second is the interesting one.</b> The first is that a file's headers
 * will not match ours — "Organisation", "Employer" and "Company Name" all mean one field — which the
 * model and {@code HeuristicColumnMatcher} between them propose an answer to, for a person to confirm.
 * The second is that a file carries columns this application has never heard of, and dropping them
 * loses the half of the list that was worth importing. Those become custom columns
 * ({@link app.lightmove.api.customcolumn}), which is why an import can add a column to a grid.
 *
 * <p><b>No cell values reach the model.</b> A spreadsheet of executives is client and candidate PII.
 * The mapping request carries each column's header and a shape computed locally from its values
 * ({@code looks like an email}, {@code looks numeric}) and nothing more.
 */
package app.lightmove.api.dataimport;
