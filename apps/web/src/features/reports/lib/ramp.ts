/**
 * UNCAVA's five-stop sequential ramp, for a quantity of one thing — executives in a pocket, in a
 * country. Pale is few and deep is many, in both themes.
 *
 * <p>Class names, never `var(--color-u-seq-n)` written into a `style`: Tailwind 4 emits a theme
 * variable only where a utility uses it, so one named only from JavaScript does not exist in light
 * mode and whatever it paints is transparent. That shipped — the heat matrix drew its filled cells
 * as white figures on nothing. Naming the utilities here is also what makes the variables exist for
 * the hub map, which has to read them as literals.
 */
export const RAMP_BG = ["bg-u-seq-1", "bg-u-seq-2", "bg-u-seq-3", "bg-u-seq-4", "bg-u-seq-5"] as const;

/** From this stop up the fill is deep enough that the label takes the ground colour. */
export const RAMP_GROUND_LABEL_FROM = 3;

// The ramp is stretched to the fullest pocket only once that pocket could fill it. Below this, one
// executive would be drawn as the deepest pocket a mandate can have.
const FULL_RAMP_FROM = 6;

/** Which stop (0–4) a count lands on, as a share of the fullest count on the same chart. */
export function rampStop(count: number, fullest: number): number {
  const top = Math.max(fullest, FULL_RAMP_FROM);
  return Math.min(RAMP_BG.length - 1, Math.floor((count / top) * RAMP_BG.length));
}
