import { describe, expect, it } from "vitest";
import { SAMPLE_REPORT } from "../../../test/sampleReport";
import { marketStats, sliceInterest } from "./marketStats";

/** The matrix's headline figures and the hub concentration, all read off the cells and hubs. */
describe("marketStats", () => {
  const stats = marketStats(SAMPLE_REPORT.market);

  it("counts the gaps, the board and the deepest pocket", () => {
    expect(stats.totalCells).toBe(24);
    expect(stats.emptyCells).toBe(7);
    expect(stats.boardTotal).toBe(5);
    expect(stats.deepest).toEqual({ sector: "FMCG", level: "C-Suite", count: 16 });
    expect(stats.placed).toBe(116);
  });

  it("lays the matrix out level by level, the fullest pocket at the deep end of the ramp", () => {
    expect(stats.rows.map((r) => r.level)).toEqual(["Board", "C-Suite", "N-1", "N-2"]);
    expect(stats.rows[1].cells[0].stop).toBe(4);
    expect(stats.rows[0].cells[1].count).toBe(0);
  });

  it("draws a rung below N-2 only once somebody is mapped at it", () => {
    const reached = marketStats({
      ...SAMPLE_REPORT.market,
      cells: [...SAMPLE_REPORT.market.cells.filter((c) => !(c.sector === "FMCG" && c.level === "N-3")), { sector: "FMCG", level: "N-3", count: 2 }],
    });

    expect(reached.rows.map((r) => r.level)).toEqual(["Board", "C-Suite", "N-1", "N-2", "N-3"]);
    expect(reached.totalCells).toBe(30);
  });

  it("keeps a lone executive at the pale end rather than drawing them as the deepest pocket", () => {
    const sparse = marketStats({
      ...SAMPLE_REPORT.market,
      sectors: ["FMCG"],
      cells: [{ sector: "FMCG", level: "C-Suite", count: 1 }],
    });

    expect(sparse.rows[1].cells[0].stop).toBe(0);
  });

  it("names the countries that hold most of the located talent", () => {
    expect(stats.located).toBe(116);
    expect(stats.topHubs).toEqual(["United Arab Emirates", "Saudi Arabia", "Kuwait"]);
    expect(stats.topHubsPct).toBe(93);
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
