/**
 * <b>Data export — the Companies grid as a file.</b> One stage of a mandate, every row of it, with
 * every column the grid draws and every column that mandate added for itself.
 *
 * <p><b>This package reads nothing of its own.</b> Companies come from {@code triagecompany}, people
 * from {@code candidate}, the extra columns from {@code customcolumn} — three seams, none depending
 * back, and {@code triagecompany} still never learns that people exist: the pairing happens here,
 * exactly as the grid pairs them in the browser. The mirror of {@code dataimport}, which is the same
 * three doors in the other direction.
 *
 * <p>The read is unpaged and capped ({@code lightmove.export.*}). Past a cap it refuses rather than
 * truncating: a short file looks complete, which is the one thing a truncated export must never do.
 */
package app.lightmove.api.dataexport;
