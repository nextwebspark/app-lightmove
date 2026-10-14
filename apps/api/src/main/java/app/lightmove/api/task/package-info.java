/**
 * <b>Tasks — what happens next with an executive, and who does it</b> (epic #862, V133). A next action is a task:
 * the Pipeline card shows each executive's earliest open one ({@code NextActionLookupAdapter} answers
 * {@code candidate}'s {@code NextActionLookup}), so the board, the drawer, My tasks and the bell read the same rows.
 *
 * <p>Depends on {@code candidate} (through {@code CandidateTaskService}: who a task is about, and its timeline
 * line), {@code project} (titles, and the {@code ProjectSeatVacated} hand-over) and {@code outreach} (the events
 * its rules file tasks from). Nothing depends back but {@code notification}.
 */
package app.lightmove.api.task;
