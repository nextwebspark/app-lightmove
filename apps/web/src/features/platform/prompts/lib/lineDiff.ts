export type DiffLineKind = "same" | "added" | "removed" | "gap";

export interface DiffLine {
  kind: DiffLineKind;
  text: string;
}

/** Unchanged lines kept on each side of a change; the rest of a long unchanged run is left out. */
const CONTEXT_LINES = 2;

/** A line-by-line comparison of two texts, in the order they read, with far-off unchanged lines collapsed. */
export function lineDiff(from: string, to: string): DiffLine[] {
  return collapsed(compared(linesOf(from), linesOf(to)));
}

function linesOf(text: string): string[] {
  return text.replace(/\r\n/g, "\n").split("\n");
}

function compared(before: string[], after: string[]): DiffLine[] {
  // Longest common subsequence, by length from each position to the end.
  const common: number[][] = Array.from({ length: before.length + 1 }, () => new Array<number>(after.length + 1).fill(0));
  for (let i = before.length - 1; i >= 0; i--) {
    for (let j = after.length - 1; j >= 0; j--) {
      common[i][j] = before[i] === after[j] ? common[i + 1][j + 1] + 1 : Math.max(common[i + 1][j], common[i][j + 1]);
    }
  }
  const lines: DiffLine[] = [];
  let i = 0;
  let j = 0;
  while (i < before.length && j < after.length) {
    if (before[i] === after[j]) {
      lines.push({ kind: "same", text: before[i] });
      i++;
      j++;
    } else if (common[i + 1][j] >= common[i][j + 1]) {
      lines.push({ kind: "removed", text: before[i++] });
    } else {
      lines.push({ kind: "added", text: after[j++] });
    }
  }
  while (i < before.length) lines.push({ kind: "removed", text: before[i++] });
  while (j < after.length) lines.push({ kind: "added", text: after[j++] });
  return lines;
}

function collapsed(lines: DiffLine[]): DiffLine[] {
  const nearAChange = lines.map((_, index) =>
    lines
      .slice(Math.max(0, index - CONTEXT_LINES), index + CONTEXT_LINES + 1)
      .some((neighbour) => neighbour.kind !== "same"),
  );
  const lastNear = nearAChange.lastIndexOf(true);
  const shown: DiffLine[] = [];
  lines.forEach((line, index) => {
    if (line.kind !== "same" || nearAChange[index]) {
      shown.push(line);
    } else if (shown.length > 0 && shown[shown.length - 1].kind !== "gap" && index < lastNear) {
      shown.push({ kind: "gap", text: "" });
    }
  });
  return shown;
}
