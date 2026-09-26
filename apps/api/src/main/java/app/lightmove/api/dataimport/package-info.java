/**
 * <b>Data import — the fourth door into a mandate's universe.</b> Writes nothing itself: it builds the
 * Companies drawer's requests for {@code triagecompany} and {@code candidate}, so every scope check,
 * duplicate rule and audit event stays where it lives. An uncovered header becomes a custom column
 * ({@link app.lightmove.api.customcolumn}). Depends on those three; none depends back.
 */
package app.lightmove.api.dataimport;
