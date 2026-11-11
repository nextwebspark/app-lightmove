export interface TreemapRow {
  key: string;
  value: number;
}

export interface TreemapTile<T extends TreemapRow = TreemapRow> {
  row: T;
  /** Percentages of the box, so a caller can style a tile without knowing its pixel size. */
  x: number;
  y: number;
  width: number;
  height: number;
}

/**
 * Squarified treemap (Bruls, Huizing and van Wijk), in percentages for a fluid box; `aspect` weighs a width percent
 * against a height percent so tiles are truly square. Largest first; zero-value rows are dropped (a hairline tile).
 */
export function squarify<T extends TreemapRow>(rows: readonly T[], aspect = 1): TreemapTile<T>[] {
  const ordered = rows.filter((row) => row.value > 0).sort((a, b) => b.value - a.value);
  const total = ordered.reduce((sum, row) => sum + row.value, 0);
  if (total === 0) return [];

  const tiles: TreemapTile<T>[] = [];
  const free = { x: 0, y: 0, width: 100, height: 100 };
  // Area in the units the layout works in — square percent, so a row's share is its share of this.
  let remaining = total;

  let index = 0;
  while (index < ordered.length) {
    const alongWidth = free.width * aspect <= free.height;
    const side = alongWidth ? free.width * aspect : free.height;
    const areaFree = free.width * aspect * free.height;

    // Take rows into the strip while doing so improves its worst aspect ratio. `ordered` runs
    // largest first, so the strip's extremes are its first row and the one just added.
    const largest = ordered[index].value;
    let taken = 1;
    let takenValue = largest;
    let worst = worstRatio(side, areaFree * (takenValue / remaining), largest, largest, takenValue);
    while (index + taken < ordered.length) {
      const smallest = ordered[index + taken].value;
      const nextValue = takenValue + smallest;
      const nextWorst = worstRatio(side, areaFree * (nextValue / remaining), largest, smallest, nextValue);
      if (nextWorst > worst) break;
      worst = nextWorst;
      takenValue = nextValue;
      taken += 1;
    }

    const strip = ordered.slice(index, index + taken);
    const depthShare = takenValue / remaining;
    let offset = 0;
    for (const row of strip) {
      const share = row.value / takenValue;
      if (alongWidth) {
        const height = free.height * depthShare;
        tiles.push({ row, x: free.x + offset, y: free.y, width: free.width * share, height });
        offset += free.width * share;
      } else {
        const width = free.width * depthShare;
        tiles.push({ row, x: free.x, y: free.y + offset, width, height: free.height * share });
        offset += free.height * share;
      }
    }

    if (alongWidth) {
      const used = free.height * depthShare;
      free.y += used;
      free.height -= used;
    } else {
      const used = free.width * depthShare;
      free.x += used;
      free.width -= used;
    }
    remaining -= takenValue;
    index += taken;
  }

  return tiles;
}

/**
 * The worst side ratio a strip of this area laid along `side` would give any of its tiles. A tile's
 * length grows with its value, so only the strip's largest and smallest members can be the worst.
 */
function worstRatio(side: number, area: number, largest: number, smallest: number, total: number): number {
  if (area === 0) return Infinity;
  const depth = area / side;
  const longest = (side * largest) / total;
  const shortest = (side * smallest) / total;
  return Math.max(longest / depth, depth / shortest);
}
