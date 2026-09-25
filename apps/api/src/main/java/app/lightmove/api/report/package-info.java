/**
 * <b>Report — a mandate's talent mapping, read as findings.</b> How far the mapping has got, where
 * the executives sit, what they have disclosed against the brief's band, and who they are by
 * nationality: four chapters over one read.
 *
 * <p><b>This package composes and owns nothing.</b> Companies come from {@code triagecompany},
 * people from {@code candidate}, the band from {@code position}, the dates from {@code project} and
 * a hub's point from {@code geocoding} — the same seams {@code talentmap} reads through, none
 * depending back. Every figure is aggregated at read time from those rows; there is no report table,
 * so nothing can go stale and nothing has to be refreshed.
 *
 * <p>Geocoding is asked only for the handful of hubs the market chapter names, and a place it has
 * not resolved yet leaves that hub without a point rather than holding the report up — the chapter
 * is a list of cities that can also be drawn, not a map that must be.
 *
 * <p>Chapter one's researcher breakdown is a second, staff-only read ({@code WORK_EXECUTE}): who
 * filed each executive comes from {@code CandidateService.addedByOf} and the team's names from
 * {@code ProjectService.teamOf}, so a client seat's read never carries the firm's own people.
 *
 * <p>The read is unpaged and capped ({@code lightmove.report.*}), with the head saying when a cap
 * was hit, so a mandate past it is told rather than shown a report that looks complete.
 */
package app.lightmove.api.report;
