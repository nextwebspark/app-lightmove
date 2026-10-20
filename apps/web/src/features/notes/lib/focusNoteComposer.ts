/**
 * Puts the caret in the note composer inside `container` once it is there: the editor loads on first use, so on the
 * first Add note it may be a few frames behind the tab that holds it.
 */
export function focusNoteComposer(containerId: string, framesLeft = 30) {
  window.requestAnimationFrame(() => {
    const editor = document.getElementById(containerId)?.querySelector<HTMLElement>('[contenteditable="true"]');
    if (editor) editor.focus();
    else if (framesLeft > 0) focusNoteComposer(containerId, framesLeft - 1);
  });
}
