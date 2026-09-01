/**
 * <b>Custom column — the columns a mandate added to its own grid.</b> One thing only: the definition
 * of an extra column on the Companies screen, and the rule for what a row may store in it.
 *
 * <p>The problem it answers is that the grid's columns were all chosen here, and a consultant's
 * spreadsheet carries columns nobody here chose — ethnicity, a client's own ranking, a notice period
 * quoted in weeks. Dropping them loses the half of the file that was worth importing; adding a real
 * column per tenant is unmigratable, unindexable, and needs the runtime role to hold the
 * {@code CREATE} privilege {@code harden.sql} exists to revoke. So the <i>values</i> live in a jsonb
 * bag on the row (V44) and this package holds only what the bag's keys mean.
 *
 * <p><b>Per project, and that is the point.</b> A new mandate starts with the built-in columns alone;
 * a mandate that imported a file carrying Ethnicity shows an Ethnicity column for as long as it runs.
 * Two mandates wanting the same extra column is two definitions, not shared state — one of them
 * renaming a header must not rename the other's.
 *
 * <p><b>The key is not the label.</b> {@code field_key} is slugged once and never rewritten, because
 * every value already stored points at it; {@code label} is the header a user reads and renames.
 * Collapsing the two would orphan a mandate's data the first time somebody fixed a typo.
 *
 * <p>{@link app.lightmove.api.customcolumn.service.CustomColumnService#applyTo} is what the rest of
 * the application calls. The bag is open, so without it any caller could write any key into a row and
 * the "columns" would be whatever happened to be in the map. {@code triagecompany} and
 * {@code candidate} depend on this package for exactly that method and for the jsonb type beside it;
 * this package depends on neither, and knows nothing about companies or people.
 */
package app.lightmove.api.customcolumn;
