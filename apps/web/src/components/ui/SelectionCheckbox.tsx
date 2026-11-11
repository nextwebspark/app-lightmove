import { useEffect, useRef, type ChangeEvent } from "react";
import { CheckBox } from "./FilterCheckRow";

/**
 * A grid's row and select-all tick box: a native checkbox (TanStack's handler reads `event.target.checked`),
 * transparent and on top of the square — a label-forwarded click drops modifier keys, which silently broke
 * shift-range in the browser while passing in jsdom. `onChange` keeps the modifiers via `nativeEvent`.
 */
export function SelectionCheckbox({
  checked,
  indeterminate,
  label,
  disabled,
  onChange,
}: {
  checked: boolean;
  /** A row that is shown but cannot be chosen, such as a company already filed. */
  disabled?: boolean;
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
        disabled={disabled}
        onChange={onChange}
        aria-label={label}
        className="peer absolute inset-0 z-10 cursor-pointer opacity-0 disabled:cursor-not-allowed"
      />
      <span className="rounded-[5px] peer-focus-visible:outline peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-u-accent peer-disabled:opacity-40">
        <CheckBox checked={indeterminate ? "mixed" : checked} size="sm" />
      </span>
    </span>
  );
}
