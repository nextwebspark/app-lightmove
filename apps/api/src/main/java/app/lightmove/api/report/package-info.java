/**
 * <b>Report</b> — a mandate's talent mapping in four chapters, aggregated live from
 * {@code triagecompany}, {@code candidate}, {@code position}, {@code project} and {@code geocoding}
 * rows, none depending back; the chapters store nothing. The researcher breakdown is a separate staff-only
 * read ({@code WORK_EXECUTE}) so a client seat never carries the firm's people, and so is the Ranking chapter,
 * which ranks the assessed executives and keeps the one table the report owns: a position's ranking scenario
 * (V204), which never writes the brief. Unpaged and capped ({@code lightmove.report.*}), the head saying when a
 * cap was hit.
 */
package app.lightmove.api.report;
