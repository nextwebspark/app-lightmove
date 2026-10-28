import type { MouseEvent } from "react";

const CONTROL = "button, a[href], input, textarea, select, [contenteditable=true]";
const FIELD = "input:not([disabled]):not([type=hidden]), textarea:not([disabled])";

/**
 * A box drawn as one field but holding chips, an icon or padding beside its real input: a press on any of that hands the
 * caret to the input, at the end of what it holds. A press on a control inside (a chip's remove, a toggle) is its own.
 * The box cannot be a `<label>` where a chip's button comes before the input — the label would press that instead.
 */
export function focusFieldWithin(event: MouseEvent<HTMLElement>) {
  const box = event.currentTarget;
  const target = event.target as HTMLElement;
  const control = target.closest(CONTROL);
  if (event.button !== 0 || !box.contains(target) || (control && box.contains(control))) return;
  const field = box.querySelector<HTMLInputElement | HTMLTextAreaElement>(FIELD);
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
