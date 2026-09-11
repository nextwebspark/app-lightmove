/**
 * The HTTP contract for a mandate's mapped executives.
 *
 * <p>{@link app.lightmove.api.candidate.dto.SaveCandidateRequest} serves both the create and the full
 * replace: the drawer holds every field and submits every field, so a field-by-field merge would have
 * to collapse "cleared" and "not sent" into one request across twenty fields.
 */
package app.lightmove.api.candidate.dto;
