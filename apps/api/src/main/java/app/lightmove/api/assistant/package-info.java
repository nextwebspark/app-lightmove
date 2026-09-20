/**
 * <b>The Uncava Assistant — a conversation that can search the mandate's market and act on it.</b>
 *
 * <p><b>A composer, like {@code talentmap} and {@code report}.</b> It owns its own rows — the thread,
 * its turns, and the event log a running turn writes — and reaches everything else through other
 * features' public service methods. Nothing depends back on it, which is what lets one feature see
 * the whole product without the dependency rule bending for it.
 *
 * <p><b>It is global, not a Strategy panel.</b> A thread belongs to a (workspace, user) pair and
 * carries a project only as the context it was asked in, so authorisation cannot come from the page
 * the question was typed on: every tool call is checked against its own arguments.
 *
 * <p>This package currently holds the seam and nothing else. The port is at the <b>turn</b> level
 * rather than at "one call to a model" — a turn is the unit that has a question, an answer, a bill
 * and a status, and abstracting below it would force every implementation down to the intersection
 * of what all of them can do, losing the provider-specific controls (caching, thinking depth,
 * effort) that are most of the reason to choose one.
 */
package app.lightmove.api.assistant;
