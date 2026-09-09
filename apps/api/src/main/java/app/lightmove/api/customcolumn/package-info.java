/**
 * <b>Custom column — the columns a mandate added to its own grid.</b> Definitions only, per project;
 * the values live in a jsonb bag on the row (V46).
 *
 * <p>{@link app.lightmove.api.customcolumn.service.CustomColumnService#applyTo} is what the rest of
 * the application calls, and the bag being open is why it has to exist. {@code triagecompany} and
 * {@code candidate} depend on this package; it depends on neither and knows nothing about companies
 * or people.
 */
package app.lightmove.api.customcolumn;
