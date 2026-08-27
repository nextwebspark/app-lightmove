/**
 * <b>Position — the mandate's role definition.</b> One brief per project: what the role is, why the
 * mandate exists, who the seat reports to, what it pays, and what a candidate is assessed against.
 * The Position screen is the only thing that reads it, and it edits the brief section by section.
 *
 * <p><b>The mandate owns two of the fields the screen shows.</b> The role title and the one target
 * date live on {@code app_lm_project}, and this package reads and writes them there. V8 retired the
 * position's own {@code start_target} for exactly that reason: two unlinked dates diverged, and the
 * date typed at project creation never reached the brief.
 *
 * <p><b>Nothing here freezes.</b> V38 retired the position lock and its {@code POSITION_UNLOCK} action
 * deliberately — a brief that had reached a readiness gate went read-only, and the mandate never
 * needed a frozen benchmark. Writes stay lenient for the same reason autosave exists: a half-typed
 * section must be persistable, so cross-field rules (minimum below maximum, a panel totalling 100)
 * are the screen's reading of the brief and never a condition of storing it.
 *
 * <p><b>What is deliberately not here:</b> people, companies and the market. A brief describes the
 * seat, not who might fill it — {@link app.lightmove.api.candidate} holds the executives mapped
 * against a mandate and {@link app.lightmove.api.strategy} holds everything about searching. This
 * package neither reads nor is read by either.
 *
 * <p>A brief cannot be reached without its mandate — the workspace scope, the role title and the
 * target date all live there — so this package depends on {@link app.lightmove.api.project}. Seeding
 * a new mandate's brief is the one call in the other direction, and it takes primitives rather than a
 * {@code Project}.
 */
package app.lightmove.api.position;
