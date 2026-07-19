import { describe, expect, it } from "vitest";
import type { Competency } from "../api/types";
import { rebalance } from "./rebalance";

const panel = (...weights: number[]): Competency[] =>
  weights.map((weight, i) => ({ name: `C${i}`, weight }));

const total = (rows: Competency[]) => rows.reduce((sum, row) => sum + row.weight, 0);

describe("rebalance", () => {
  it("keeps the panel total constant while moving one slider", () => {
    const next = rebalance(panel(30, 30, 20, 20), 0, 50);
    expect(next[0].weight).toBe(50);
    expect(total(next)).toBe(100);
  });

  it("redistributes proportionally to the other rows' weights", () => {
    const next = rebalance(panel(40, 40, 20), 2, 0);
    // The freed 20 splits 50/50 between two equal rows.
    expect(next[0].weight).toBe(50);
    expect(next[1].weight).toBe(50);
  });

  it("clamps the target into 0–100", () => {
    expect(rebalance(panel(50, 50), 0, 180)[0].weight).toBe(100);
    expect(rebalance(panel(50, 50), 0, -20)[0].weight).toBe(0);
  });

  it("shares equally when every other row is at zero", () => {
    const next = rebalance(panel(100, 0, 0), 0, 40);
    expect(total(next)).toBe(100);
    expect(next[1].weight + next[2].weight).toBe(60);
  });

  it("lands rounding drift so the total stays exact", () => {
    const next = rebalance(panel(33, 33, 34), 0, 50);
    expect(total(next)).toBe(100);
  });

  it("returns the rows untouched when nothing changes", () => {
    const rows = panel(60, 40);
    expect(rebalance(rows, 1, 40)).toBe(rows);
  });

  it("never leaves a copy mutated — the input is untouched", () => {
    const rows = panel(30, 70);
    rebalance(rows, 0, 90);
    expect(rows[0].weight).toBe(30);
    expect(rows[1].weight).toBe(70);
  });
});
