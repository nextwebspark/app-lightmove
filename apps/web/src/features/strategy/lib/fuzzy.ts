import type { SectorCount } from "../api/types";

export interface RankedSector {
  name: string;
  count: number;
  /** The indices of `name` that matched the query — the combobox bolds these. */
  matched: number[];
}

/**
 * A ranked, fuzzy match over the sector list — "results similar to what the user is typing", not a
 * plain prefix filter. A whole-string prefix beats a word-boundary prefix beats a substring beats a
 * loose subsequence; within a tier the more populous sector wins, so a search that could mean several
 * things surfaces the one a consultant most likely wants first.
 */
export function rankSectors(query: string, sectors: SectorCount[], limit = 8): RankedSector[] {
  const q = query.trim().toLowerCase();
  if (!q) return [];

  const scored: { sector: SectorCount; score: number; matched: number[] }[] = [];
  for (const sector of sectors) {
    const match = scoreSector(q, sector.name.toLowerCase());
    if (match) {
      scored.push({ sector, score: match.score, matched: match.matched });
    }
  }

  scored.sort((a, b) => b.score - a.score || b.sector.count - a.sector.count);
  return scored
    .slice(0, limit)
    .map(({ sector, matched }) => ({ name: sector.name, count: sector.count, matched }));
}

/** Tiered score plus the matched character indices, or null when the query does not match at all. */
function scoreSector(q: string, name: string): { score: number; matched: number[] } | null {
  if (name.startsWith(q)) {
    return { score: 1000, matched: range(0, q.length) };
  }

  const wordStart = wordBoundaryIndex(name, q);
  if (wordStart >= 0) {
    return { score: 800 - wordStart, matched: range(wordStart, wordStart + q.length) };
  }

  const at = name.indexOf(q);
  if (at >= 0) {
    return { score: 500 - at, matched: range(at, at + q.length) };
  }

  const subsequence = subsequenceIndices(name, q);
  if (subsequence) {
    // Tighter matches (fewer gaps between hit characters) rank higher.
    const span = subsequence[subsequence.length - 1] - subsequence[0];
    return { score: 200 - span, matched: subsequence };
  }

  return null;
}

/** The index where a word in `name` begins with `q` (space- or punctuation-delimited), else -1. */
function wordBoundaryIndex(name: string, q: string): number {
  for (let i = 0; i < name.length; i++) {
    const atBoundary = i === 0 || !isWordChar(name[i - 1]);
    if (atBoundary && i > 0 && name.startsWith(q, i)) {
      return i;
    }
  }
  return -1;
}

function isWordChar(ch: string): boolean {
  return /[a-z0-9]/i.test(ch);
}

/** Greedy left-to-right subsequence match: the indices of `name` that spell `q`, or null. */
function subsequenceIndices(name: string, q: string): number[] | null {
  const indices: number[] = [];
  let qi = 0;
  for (let i = 0; i < name.length && qi < q.length; i++) {
    if (name[i] === q[qi]) {
      indices.push(i);
      qi++;
    }
  }
  return qi === q.length ? indices : null;
}

function range(start: number, end: number): number[] {
  return Array.from({ length: end - start }, (_, i) => start + i);
}
