import { describe, expect, it } from "vitest";
import { SAMPLE_REPORT } from "../mock/sampleReport";
import { marketStats, sliceInterest } from "./marketStats";

/** The matrix's headline figures and the hub concentration, all read off the cells and hubs. */
describe("marketStats", () => {
  const stats = marketStats(SAMPLE_REPORT.market);

  it("counts the gaps, the board and the deepest pocket", () => {
    expect(stats.totalCells).toBe(24);
    expect(stats.emptyCells).toBe(7);
    expect(stats.boardTotal).toBe(5);
    expect(stats.deepest).toEqual({ sector: "FMCG", level: "C-Suite", count: 16 });
    expect(stats.executivesMapped).toBe(116);
  });

  it("lays the matrix out level by level with an intensity per cell", () => {
    expect(stats.rows.map((r) => r.level)).toEqual(["Board", "C-Suite", "N-1", "N-2"]);
    const fmcgCSuite = stats.rows[1].cells[0];
    expect(fmcgCSuite.intensity).toBe(1);
    expect(stats.rows[0].cells[1].count).toBe(0);
  });

  it("names the hubs that hold most of the talent", () => {
    expect(stats.hubTotal).toBe(116);
    expect(stats.topHubs).toEqual(["Dubai", "Riyadh", "Jeddah"]);
    expect(stats.topHubsPct).toBe(72);
  });
});

describe("sliceInterest", () => {
  it("counts the listed executives by status, folding verified into passive", () => {
    expect(
      sliceInterest([{ status: "interested" }, { status: "verified" }, { status: "passive" }, { status: "offlimits" }]),
    ).toEqual({ interested: 1, passive: 2, offLimits: 1 });
  });
});
