import { describe, expect, it } from "vitest";
import type { SectorCount } from "../api/types";
import { rankSectors } from "./fuzzy";

const sectors: SectorCount[] = [
  { name: "Retail", count: 1299 },
  { name: "Retail Apparel and Fashion", count: 300 },
  { name: "Food and Beverage Services", count: 792 },
  { name: "Oil and Gas", count: 1526 },
  { name: "Hospitality", count: 1354 },
];

describe("rankSectors", () => {
  it("returns nothing for an empty query", () => {
    expect(rankSectors("  ", sectors)).toEqual([]);
  });

  it("ranks a whole-string prefix above a substring", () => {
    const ranked = rankSectors("retail", sectors);
    // Both "Retail" and "Retail Apparel…" prefix-match; the exact/shorter, more populous one leads.
    expect(ranked[0].name).toBe("Retail");
    expect(ranked.map((r) => r.name)).toContain("Retail Apparel and Fashion");
  });

  it("matches a word inside the name, not just its start", () => {
    const ranked = rankSectors("bev", sectors);
    expect(ranked[0].name).toBe("Food and Beverage Services");
    // The match points at the start of "Beverage", not the start of the string.
    expect(ranked[0].matched[0]).toBe("Food and ".length);
  });

  it("tolerates a loose subsequence when nothing tighter matches", () => {
    const ranked = rankSectors("ols", sectors);
    expect(ranked.map((r) => r.name)).toContain("Oil and Gas");
  });

  it("breaks ties by company count, most populous first", () => {
    const tied: SectorCount[] = [
      { name: "Retail A", count: 10 },
      { name: "Retail B", count: 90 },
    ];
    expect(rankSectors("retail", tied).map((r) => r.name)).toEqual(["Retail B", "Retail A"]);
  });

  it("excludes names the query cannot match at all", () => {
    expect(rankSectors("zzz", sectors)).toEqual([]);
  });
});
