/**
 * The HTTP contract for a mandate's custom grid columns: defining one, renaming or retyping it,
 * hiding it, reordering the set, and removing one.
 *
 * <p>Only the definitions are here. A column's <i>values</i> travel on the row that holds them — the
 * {@code customFields} map on the triage-company and candidate payloads — because they are edited in
 * the same drawer, and in the same save, as every built-in field beside them.
 */
package app.lightmove.api.customcolumn.dto;
