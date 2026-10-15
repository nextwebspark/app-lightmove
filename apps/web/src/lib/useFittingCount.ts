import { useLayoutEffect, useState, type RefObject } from "react";
import { pillsThatFit } from "./pillsThatFit";

/** Spread on each pill `useFittingCount` measures; one past the count also wears {@link LEFT_OUT_PILL}. */
export const FITTING_PILL = { "data-fitting-pill": "" } as const;

/**
 * A pill the row has no room for: out of the flow and unseen, but still laid out at its natural width so
 * the next measurement can read it. Rendered rather than dropped, so each value is in the row once.
 */
export const LEFT_OUT_PILL = "invisible absolute start-0 top-0";

/**
 * How many of `slot`'s pills fit in `lines` lines, keeping room for `chip` (a hidden copy of the "+N"
 * chip at its widest) and `tail`, measured again whenever the slot is resized and once the page's fonts
 * have loaded, since a pill first drawn in a fallback font is measured at the wrong width. Hiding pills
 * changes the row's height, so the observer fires once more after each change; it measures the same count,
 * and React keeps the state as it is.
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
    let isMounted = true;
    void document.fonts?.ready.then(() => {
      if (isMounted) measure();
    });
    const observer = typeof ResizeObserver === "undefined" ? null : new ResizeObserver(measure);
    observer?.observe(row);
    return () => {
      isMounted = false;
      observer?.disconnect();
    };
  }, [slot, chip, tail, lines, contentKey]);

  return count;
}
