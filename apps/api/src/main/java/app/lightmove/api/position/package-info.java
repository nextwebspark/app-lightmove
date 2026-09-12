/**
 * <b>Position — the mandate's role definition.</b> One brief per project, edited section by section
 * and drafted from the role-template library (V42) rather than starting blank.
 *
 * <p>The role title and the one target date live on {@code app_lm_project}, not here. The title is
 * read and written there; the target date is only read. V8 retired the position's own
 * {@code start_target} because two unlinked dates diverged and the date typed at project creation
 * never reached the brief.
 *
 * <p>Nothing here freezes — V38 retired the position lock deliberately — and writes stay lenient so
 * autosave can persist a half-typed section. Depends on {@link app.lightmove.api.project}; seeding a
 * new mandate's brief is the one call the other way, and it takes primitives.
 */
package app.lightmove.api.position;
