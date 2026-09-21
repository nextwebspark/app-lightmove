import { describe, expect, it } from "vitest";
import { squarify, type TreemapRow, type TreemapTile } from "./treemap";

const rows = (...values: number[]): TreemapRow[] =>
  values.map((value, index) => ({ key: `r${index}`, value }));

const area = (tile: TreemapTile) => tile.width * tile.height;

/** Do any two tiles overlap? Touching edges do not count. */
function overlaps(tiles: TreemapTile[]): boolean {
  const EPS = 1e-9;
  return tiles.some((a, i) =>
    tiles.slice(i + 1).some(
      (b) =>
        a.x + a.width > b.x + EPS &&
        b.x + b.width > a.x + EPS &&
        a.y + a.height > b.y + EPS &&
        b.y + b.height > a.y + EPS,
    ),
  );
}

describe("squarify", () => {
  it("gives every tile the share of the box its value is of the total", () => {
    const tiles = squarify(rows(55, 34, 20, 12, 8, 3));
    const total = 132;

    expect(tiles).toHaveLength(6);
    for (const tile of tiles) {
      expect(area(tile)).toBeCloseTo((tile.row.value / total) * 10_000, 6);
    }
  });

  it("tiles the whole box without overlapping", () => {
    const tiles = squarify(rows(55, 34, 20, 12, 8, 3));

    expect(tiles.reduce((sum, tile) => sum + area(tile), 0)).toBeCloseTo(10_000, 6);
    expect(overlaps(tiles)).toBe(false);
    for (const tile of tiles) {
      expect(tile.x).toBeGreaterThanOrEqual(-1e-9);
      expect(tile.y).toBeGreaterThanOrEqual(-1e-9);
      expect(tile.x + tile.width).toBeLessThanOrEqual(100 + 1e-9);
      expect(tile.y + tile.height).toBeLessThanOrEqual(100 + 1e-9);
    }
  });

  it("lays the largest row first, at the origin, whatever order it arrived in", () => {
    const tiles = squarify(rows(3, 55, 20));

    expect(tiles[0].row.value).toBe(55);
    expect(tiles[0].x).toBe(0);
    expect(tiles[0].y).toBe(0);
    expect(tiles.map((tile) => tile.row.value)).toEqual([55, 20, 3]);
  });

  it("keeps tiles near square rather than laying them out in strips", () => {
    // Twenty equal rows: a naive slice-and-dice gives 20:1 slivers.
    const tiles = squarify(rows(...Array<number>(20).fill(5)));

    for (const tile of tiles) {
      const ratio = Math.max(tile.width / tile.height, tile.height / tile.width);
      expect(ratio).toBeLessThan(3);
    }
  });

  it("squarifies against the box's real shape, not against a square", () => {
    // A 4:1 box of four equal rows should come out as four squares in a line, not four 16:1 slivers.
    const tiles = squarify(rows(1, 1, 1, 1), 4);

    for (const tile of tiles) {
      const ratio = Math.max((tile.width * 4) / tile.height, tile.height / (tile.width * 4));
      expect(ratio).toBeCloseTo(1, 6);
    }
  });

  it("gives a single row the whole box", () => {
    expect(squarify(rows(7))).toEqual([
      { row: { key: "r0", value: 7 }, x: 0, y: 0, width: 100, height: 100 },
    ]);
  });

  it("drops rows with nothing to show rather than drawing a hairline", () => {
    expect(squarify(rows(0, 5, 0)).map((tile) => tile.row.key)).toEqual(["r1"]);
    expect(squarify(rows())).toEqual([]);
    expect(squarify(rows(0, 0))).toEqual([]);
  });
});
