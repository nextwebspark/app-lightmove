import type { MouseEvent } from "react";

const CONTROL = "button, a[href], input, textarea, select, [contenteditable=true]";
const FIELD = "input:not([disabled]):not([type=hidden]), textarea:not([disabled])";

/**
 * A left press on the box itself — its padding, a chip's label, an icon — rather than on one of its own controls, or on
 * a portalled list that only bubbles through it in React. `alsoControls` names more of the box's own that keep a press.
 */
export function isPressOnEmptySpace(event: MouseEvent<HTMLElement>, alsoControls?: string): boolean {
  const box = event.currentTarget;
  const target = event.target as HTMLElement;
  if (event.button !== 0 || !box.contains(target)) return false;
  const control = target.closest(alsoControls ? `${CONTROL}, ${alsoControls}` : CONTROL);
  return !control || !box.contains(control);
}

/**
 * A box drawn as one field but holding chips, an icon or padding beside its real input: a press on any of that hands the
 * caret to the box's first field, at the end of what it holds. The box cannot be a `<label>` where a chip's button comes
 * before the input — the label would press that instead.
 */
export function focusFieldWithin(event: MouseEvent<HTMLElement>) {
  if (!isPressOnEmptySpace(event)) return;
  const field = event.currentTarget.querySelector<HTMLInputElement | HTMLTextAreaElement>(FIELD);
  if (!field) return;
  event.preventDefault();
  field.focus();
  try {
    // A field that selects its answer on focus keeps that selection.
    if (field.selectionStart === field.selectionEnd) field.setSelectionRange(field.value.length, field.value.length);
  } catch {
    // An input type with no caret (email, number) refuses a selection; focus alone is enough there.
  }
}
