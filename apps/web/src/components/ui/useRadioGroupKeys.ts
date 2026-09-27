import { useRef, type KeyboardEvent } from "react";

/**
 * The radio group's keyboard: one tab stop on the chosen option, and the arrows move the choice with
 * the focus following it. Spread `ref` and `onKeyDown` onto the `role="radiogroup"` element; its
 * buttons must be the options, in `values` order. With nothing chosen yet, the arrows start from
 * either end.
 */
export function useRadioGroupKeys<TValue extends string>(
  values: readonly TValue[],
  value: TValue | null,
  onChange: (value: TValue) => void,
) {
  const ref = useRef<HTMLDivElement>(null);

  const onKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    const forward = event.key === "ArrowRight" || event.key === "ArrowDown";
    const back = event.key === "ArrowLeft" || event.key === "ArrowUp";
    if (!forward && !back) return;
    event.preventDefault();
    const index = value === null ? -1 : values.indexOf(value);
    const nextIndex =
      index < 0 ? (forward ? 0 : values.length - 1) : (index + (forward ? 1 : -1) + values.length) % values.length;
    onChange(values[nextIndex]);
    ref.current?.querySelectorAll("button")[nextIndex]?.focus();
  };

  return { ref, onKeyDown };
}
