/**
 * <b>Report — a mandate's talent mapping, read as findings.</b> How far the mapping has got, where
 * the executives sit, what they have disclosed against the brief's band, and who they are by
 * nationality: four chapters over one read.
 *
 * <p><b>This package composes and owns nothing.</b> Companies come from {@code triagecompany},
 * people from {@code candidate}, the band from {@code position} and the dates from {@code project}
 * — the same seams {@code talentmap} reads through, none depending back. Every figure is aggregated
 * at read time from those rows; there is no report table, so nothing can go stale and nothing has
 * to be refreshed.
 *
 * <p>The read is unpaged and capped ({@code lightmove.report.*}), with the head saying when a cap
 * was hit, so a mandate past it is told rather than shown a report that looks complete.
 */
package app.lightmove.api.report;
