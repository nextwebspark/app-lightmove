/**
 * <b>The position brief's HTTP contract.</b> Reading is one call for the whole brief; writing is a
 * snapshot PUT per section, each answering with the whole brief again.
 *
 * <p><b>Writes are deliberately lenient.</b> Autosave has to be free to persist a half-typed section,
 * so there are no cross-field rules here. Size and range ceilings are enforced; agreement between
 * fields is not.
 *
 * <p>Everything is project-scoped and seat-gated, with the workspace coming from the principal
 * rather than the path.
 */
package app.lightmove.api.position.dto;
