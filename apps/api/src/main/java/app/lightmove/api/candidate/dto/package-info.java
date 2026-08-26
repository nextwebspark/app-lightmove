/**
 * The HTTP contract for a mandate's mapped executives: adding one, replacing one whole, reading them
 * back a page at a time, and removing one.
 *
 * <p>{@link app.lightmove.api.candidate.dto.SaveCandidateRequest} serves both writes. The drawer that
 * edits a candidate holds every field and submits every field, so a create and a full replace are the
 * same payload — and a field-by-field merge would have to invent a meaning for an omitted field across
 * twenty of them, collapsing "cleared" and "not sent" into one request.
 *
 * <p>Reads are project-scoped and seat-gated, exactly as
 * {@link app.lightmove.api.triagecompany.dto} is: a client representative may read a mandate's people
 * and change nothing about them.
 */
package app.lightmove.api.candidate.dto;
