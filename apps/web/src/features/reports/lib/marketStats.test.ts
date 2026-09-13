import { describe, expect, it } from "vitest";
import { SAMPLE_REPORT } from "../mock/sampleReport";
import { marketStats, sliceInterest } from "./marketStats";

/** The matrix's headline figures and the hub concentration, all read off the cells and hubs. */
describe("marketStats", () => {
  const stats = marketStats(SAMPLE_REPORT.market);

  it("counts the gaps, the board and the deepest pocket", () => {
    expect(stats.totalCells).toBe(30);
    expect(stats.emptyCells).toBe(13);
    expect(stats.boardTotal).toBe(5);
    expect(stats.deepest).toEqual({ sector: "FMCG", level: "C-Suite", count: 16 });
    expect(stats.placed).toBe(116);
  });

  it("lays the matrix out level by level with an intensity per cell", () => {
    expect(stats.rows.map((r) => r.level)).toEqual(["Board", "C-Suite", "N-1", "N-2", "N-3"]);
    expect(stats.rows[1].cells[0].intensity).toBe(1);
    expect(stats.rows[0].cells[1].count).toBe(0);
  });

  it("names the hubs that hold most of the located talent", () => {
    expect(stats.located).toBe(116);
    expect(stats.topHubs).toEqual(["Dubai", "Riyadh", "Jeddah"]);
    expect(stats.topHubsPct).toBe(72);
  });

  it("has nothing to place on an empty matrix", () => {
    const empty = marketStats({ ...SAMPLE_REPORT.market, sectors: [], cells: [], hubs: [], elsewhere: 0 });

    expect(empty.deepest).toBeNull();
    expect(empty.totalCells).toBe(0);
    expect(empty.topHubsPct).toBe(0);
  });
});

describe("sliceInterest", () => {
  it("counts a yes, a not-yet and a closed door", () => {
    expect(
      sliceInterest([
        { status: "interested" },
        { status: "engaged" },
        { status: "identified" },
        { status: "offLimits" },
        { status: "notInterested" },
      ]),
    ).toEqual({ interested: 1, passive: 2, closed: 2 });
  });
});
