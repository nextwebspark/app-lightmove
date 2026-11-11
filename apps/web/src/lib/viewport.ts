/** Where the shell stops overlaying its rails and lays them out beside the content — Tailwind's `lg`. */
export const SHELL_MIN_WIDTH = "64rem";

/**
 * Whether the rails have room beside the content, read once for an initial disclosure state CSS cannot express. It does
 * not subscribe; anything that must follow the viewport belongs in a Tailwind variant.
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
