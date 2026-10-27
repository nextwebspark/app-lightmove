/** Where the shell stops overlaying its rails and lays them out beside the content — Tailwind's `lg`. */
export const SHELL_MIN_WIDTH = "64rem";

/**
 * Whether the rails have room to sit beside the content.
 *
 * <p>Read once, for the initial state of a disclosure that CSS cannot express — a filter rail that
 * should start open on a desktop and closed where it would cover the results. It does not subscribe,
 * so nothing here re-renders on resize; anything that must *follow* the viewport belongs in a
 * Tailwind variant instead.
 */
export function hasRoomForRails(): boolean {
  return window.matchMedia(`(min-width: ${SHELL_MIN_WIDTH})`).matches;
}

/** Where a list's cards give way to its table — Tailwind's `md`. */
export const TABLE_MIN_WIDTH = "48rem";

/**
 * Whether the viewport is narrower than a table needs, for a component that draws a different structure on a phone
 * (cards for rows, one day for a week). Read once at mount, as {@link hasRoomForRails} is: a rotation or a resize keeps
 * the layout it opened with until the next mount.
 */
export function isBelowTableWidth(): boolean {
  return !window.matchMedia(`(min-width: ${TABLE_MIN_WIDTH})`).matches;
}
