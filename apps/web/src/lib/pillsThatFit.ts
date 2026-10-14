export interface PillRowLayout {
  gap: number;
  lines: number;
  /** The "+N" chip, drawn only when some pills are left out. */
  chip: number;
  /** What always follows the pills, such as an add button. */
  tail?: readonly number[];
}

/**
 * How many pills, in order, a wrapping row of `available` width holds within `lines` lines — keeping room
 * for the chip and the tail on the last line whenever any pill is left out.
 */
export function pillsThatFit(widths: readonly number[], available: number, layout: PillRowLayout): number {
  const { gap, lines, chip, tail = [] } = layout;
  if (fitsWithin([...widths, ...tail], available, gap, lines)) return widths.length;
  for (let count = widths.length - 1; count > 0; count--) {
    if (fitsWithin([...widths.slice(0, count), chip, ...tail], available, gap, lines)) return count;
  }
  return 0;
}

function fitsWithin(items: readonly number[], available: number, gap: number, lines: number): boolean {
  let line = 1;
  let used = 0;
  let isLineStart = true;
  for (const item of items) {
    const width = Math.min(item, available);
    if (isLineStart) {
      used = width;
      isLineStart = false;
    } else if (used + gap + width <= available) {
      used += gap + width;
    } else {
      line += 1;
      if (line > lines) return false;
      used = width;
    }
  }
  return true;
}
