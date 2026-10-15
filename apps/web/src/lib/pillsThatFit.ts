export interface PillRowLayout {
  gap: number;
  lines: number;
  /** The "+N" chip, drawn only when some pills are left out. */
  chip: number;
  /** What always follows the pills, such as an add button. */
  tail?: readonly number[];
}

interface WrapState {
  line: number;
  used: number;
  isLineStart: boolean;
}

const ROW_START: WrapState = { line: 1, used: 0, isLineStart: true };

/**
 * How many pills, in order, a wrapping row of `available` width holds within `lines` lines — keeping room
 * for the chip and the tail on the last line whenever any pill is left out. The pills are wrapped once, and
 * each shorter count is tried from where its own pills left the row.
 */
export function pillsThatFit(widths: readonly number[], available: number, layout: PillRowLayout): number {
  const { gap, lines, chip, tail = [] } = layout;
  const place = (state: WrapState, item: number) => placed(state, item, available, gap);
  const fits = (state: WrapState, after: readonly number[]) => after.reduce(place, state).line <= lines;

  const afterPills = [ROW_START];
  for (const width of widths) afterPills.push(place(afterPills[afterPills.length - 1], width));
  if (fits(afterPills[widths.length], tail)) return widths.length;

  const chipAndTail = [chip, ...tail];
  for (let count = widths.length - 1; count > 0; count--) {
    if (fits(afterPills[count], chipAndTail)) return count;
  }
  return 0;
}

function placed(state: WrapState, item: number, available: number, gap: number): WrapState {
  const width = Math.min(item, available);
  if (state.isLineStart) return { line: state.line, used: width, isLineStart: false };
  if (state.used + gap + width <= available) return { ...state, used: state.used + gap + width };
  return { line: state.line + 1, used: width, isLineStart: false };
}
