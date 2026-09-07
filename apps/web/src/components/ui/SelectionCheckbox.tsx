import type { MouseEvent } from "react";
import { CheckBox } from "./FilterCheckRow";

/**
 * The tick box a selectable grid puts in front of every row, and the select-all in front of its
 * header.
 *
 * <p>A `role="checkbox"` button rather than an `<input>`, so it wears the same square as the filter
 * rail — and because the header's box is tri-state: `"mixed"` for a page that is part-selected, which
 * `aria-checked` expresses and a native checkbox only reaches through an imperative DOM property.
 *
 * <p>The handler is given the shift key rather than the event. Shift-click extending a range from the
 * last box touched is the gesture every file manager and data grid has taught, and the alternative —
 * ticking forty rows one at a time — is the thing the selection bar exists to avoid.
 */
export function SelectionCheckbox({
  checked,
  label,
  onToggle,
}: {
  checked: boolean | "mixed";
  /** The accessible name — "Select Emirates NBD", "Select all companies on this page". */
  label: string;
  onToggle: (extend: boolean) => void;
}) {
  const handleClick = (event: MouseEvent<HTMLButtonElement>) => {
    // A shift-click inside a grid otherwise selects the text of every row it crossed.
    if (event.shiftKey) window.getSelection()?.removeAllRanges();
    onToggle(event.shiftKey);
  };

  return (
    <button
      type="button"
      role="checkbox"
      aria-checked={checked === "mixed" ? "mixed" : checked}
      aria-label={label}
      onClick={handleClick}
      className="-m-2 grid flex-none place-items-center p-2"
    >
      <CheckBox checked={checked} size="sm" />
    </button>
  );
}
