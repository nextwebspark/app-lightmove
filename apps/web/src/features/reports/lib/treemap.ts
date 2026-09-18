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
 * Squarified treemap: the rows tile the whole box, each one taking the share of the area its value
 * is of the total, laid out in rows or columns chosen to keep every tile as near square as it can
 * be. Bruls, Huizing and van Wijk's algorithm.
 *
 * <p>Percentages rather than pixels, because the block is fluid — the caller gives it a CSS aspect
 * ratio and the tiles follow the width it happens to get. `aspect` is what a percentage of width and
 * a percentage of height are worth against each other; without it the squarifying would call a tile
 * square that is drawn twice as wide as it is tall.
 *
 * <p>Rows are laid largest first, so the leader lands top-left where the eye starts. A row with no
 * value takes no area and is dropped: a zero-area tile is a hairline that reads as a rendering
 * fault, and its label would have nowhere to sit.
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

    // Take rows into the strip while doing so improves its worst aspect ratio.
    let taken = 1;
    let takenValue = ordered[index].value;
    let worst = worstRatio(side, areaFree * (takenValue / remaining), [ordered[index].value], takenValue);
    while (index + taken < ordered.length) {
      const nextValue = takenValue + ordered[index + taken].value;
      const values = ordered.slice(index, index + taken + 1).map((row) => row.value);
      const nextWorst = worstRatio(side, areaFree * (nextValue / remaining), values, nextValue);
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

/** The worst side ratio a strip of this area laid along `side` would give any of its tiles. */
function worstRatio(side: number, area: number, values: number[], total: number): number {
  if (area === 0) return Infinity;
  const depth = area / side;
  return Math.max(
    ...values.map((value) => {
      const length = (side * value) / total;
      return Math.max(length / depth, depth / length);
    }),
  );
}
