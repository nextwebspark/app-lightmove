/**
 * <b>The position brief's HTTP contract.</b> Reading is one call returning the whole brief. Writing is
 * a snapshot PUT per section — the scalars, the screening criteria and the competency panels each have
 * their own — and every one of them answers with the whole brief again, so the screen never has to
 * assemble state from a partial reply.
 *
 * <p><b>A section is the write unit because a section is the edit unit.</b> The screen has no Save
 * button: it autosaves whatever the consultant is editing. One whole-document PUT would resend the
 * sections nobody touched on every keystroke, and a serialisation slip in one of them would then
 * quietly blank another.
 *
 * <p><b>Writes are deliberately lenient.</b> Autosave has to be free to persist a half-typed section,
 * so there are no cross-field rules here. Size and range ceilings are enforced; agreement between
 * fields is not.
 *
 * <p>Everything here is project-scoped and seat-gated: reading needs a seat on the mandate, writing
 * needs the edit action on it, and the workspace comes from the principal rather than the path.
 */
package app.lightmove.api.position.dto;
