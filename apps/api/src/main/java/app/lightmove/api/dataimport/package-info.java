/**
 * <b>Data import — the fourth door into a mandate's universe.</b> Read a consultant's spreadsheet,
 * work out what its columns mean, and write what it carries through the doors that already exist.
 *
 * <p><b>This package writes nothing itself.</b> It builds the requests the Companies drawer posts and
 * hands them to {@code triagecompany} and {@code candidate}, so every scope check, duplicate rule and
 * audit event stays where it already lives. A header no field covers becomes a custom column
 * ({@link app.lightmove.api.customcolumn}). It depends on those three; none of them depends back.
 */
package app.lightmove.api.dataimport;
