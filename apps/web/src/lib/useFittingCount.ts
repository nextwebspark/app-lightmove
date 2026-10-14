import { useLayoutEffect, useState, type RefObject } from "react";
import { pillsThatFit } from "./pillsThatFit";

/** Spread on each pill `useFittingCount` measures; one past the count also wears {@link LEFT_OUT_PILL}. */
export const FITTING_PILL = { "data-fitting-pill": "" } as const;

/**
 * A pill the row has no room for: out of the flow and unseen, but still laid out at its natural width so
 * the next measurement can read it. Rendered rather than dropped, so each value is in the DOM once.
 */
export const LEFT_OUT_PILL = "invisible absolute start-0 top-0";

/**
 * How many of `slot`'s pills fit in `lines` lines, keeping room for `chip` (a hidden copy of the "+N"
 * chip at its widest) and `tail`, measured again whenever the slot is resized.
 */
export function useFittingCount({
  slot,
  chip,
  tail,
  lines,
  contentKey,
}: {
  slot: RefObject<HTMLElement | null>;
  chip: RefObject<HTMLElement | null>;
  tail?: RefObject<HTMLElement | null>;
  lines: number;
  /** Changes whenever the pills do. */
  contentKey: string;
}): number {
  const [count, setCount] = useState(Number.POSITIVE_INFINITY);

  useLayoutEffect(() => {
    const row = slot.current;
    if (!row) return;
    const widthOf = (element: Element | null | undefined) => Math.ceil(element?.getBoundingClientRect().width ?? 0);
    const measure = () =>
      setCount(
        pillsThatFit(Array.from(row.querySelectorAll("[data-fitting-pill]"), widthOf), row.clientWidth, {
          gap: parseFloat(getComputedStyle(row).columnGap) || 0,
          lines,
          chip: widthOf(chip.current),
          tail: tail?.current ? [widthOf(tail.current)] : [],
        }),
      );
    measure();
    if (typeof ResizeObserver === "undefined") return;
    const observer = new ResizeObserver(measure);
    observer.observe(row);
    return () => observer.disconnect();
  }, [slot, chip, tail, lines, contentKey]);

  return count;
}
