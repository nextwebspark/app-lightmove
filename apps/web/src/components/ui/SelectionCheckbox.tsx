import { useEffect, useRef, type ChangeEvent } from "react";
import { CheckBox } from "./FilterCheckRow";

/**
 * The tick box a selectable grid puts in front of every row, and the select-all in front of its
 * header.
 *
 * <p>A real `<input type="checkbox">`, transparent and stretched over the square the filter rail
 * already uses. Native rather than a styled `role="checkbox"` button for two reasons: it comes with
 * the semantics free — announced as a checkbox, toggled by Space — and TanStack's
 * `getToggleSelectedHandler` reads `event.target.checked` off the event it is handed, which a button
 * does not have.
 *
 * <p><b>The input sits on top and takes the click itself; it is never merely `sr-only` behind the
 * square.</b> Hidden behind it, every click lands on the wrapper and reaches the control only by
 * label forwarding — and a label-forwarded click does not carry the modifier keys, so shift-range
 * silently collapsed to a single toggle in the browser while passing in jsdom, which forwards
 * differently. Transparent-on-top means the shift-click the user makes is the event the handler
 * reads.
 *
 * <p>`onChange` rather than `onClick`, because React's synthetic change event carries the original
 * click as `nativeEvent` — so the modifiers survive, and the input stays controlled.
 */
export function SelectionCheckbox({
  checked,
  indeterminate,
  label,
  onChange,
}: {
  checked: boolean;
  /** A select-all over a part-selected page: a dash, not a tick. */
  indeterminate?: boolean;
  /** The accessible name — "Select Emirates NBD", "Select all companies on this page". */
  label: string;
  onChange: (event: ChangeEvent<HTMLInputElement>) => void;
}) {
  const inputRef = useRef<HTMLInputElement>(null);

  // The tri-state has no attribute — it is a DOM property, so React cannot express it in JSX and it
  // has to be written after every render that changes it.
  useEffect(() => {
    if (inputRef.current) inputRef.current.indeterminate = indeterminate === true;
  }, [indeterminate]);

  return (
    <span className="relative -m-2 grid flex-none place-items-center p-2">
      {/* Before the square in the DOM so `peer-*` can reach it: CSS has no previous-sibling selector. */}
      <input
        ref={inputRef}
        type="checkbox"
        checked={checked}
        onChange={onChange}
        aria-label={label}
        className="peer absolute inset-0 z-10 cursor-pointer opacity-0"
      />
      <span className="rounded-[5px] peer-focus-visible:outline peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-amber">
        <CheckBox checked={indeterminate ? "mixed" : checked} size="sm" />
      </span>
    </span>
  );
}
