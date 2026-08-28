import { describe, expect, it } from "vitest";
import type { Competency } from "../api/types";
import { rebalance } from "./rebalance";

const panel = (...weights: number[]): Competency[] =>
  weights.map((weight, i) => ({ name: `C${i}`, description: null, weight }));

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

describe("rebalance with locked rows", () => {
  const lock = (...indices: number[]) => new Set(indices);

  it("leaves a locked row exactly where it was", () => {
    const next = rebalance(panel(30, 30, 20, 20), 0, 50, lock(1));
    expect(next[1].weight).toBe(30);
    expect(next[0].weight).toBe(50);
    expect(total(next)).toBe(100);
  });

  it("puts the whole difference on the one row still free to move", () => {
    const next = rebalance(panel(40, 30, 30), 0, 60, lock(1));
    expect(next[0].weight).toBe(60);
    expect(next[1].weight).toBe(30);
    expect(next[2].weight).toBe(10);
    expect(total(next)).toBe(100);
  });

  it("refuses to move a row when everything else is locked", () => {
    const rows = panel(40, 30, 30);
    expect(rebalance(rows, 0, 60, lock(1, 2))).toBe(rows);
  });

  it("clamps at what the locked rows are not holding", () => {
    // 70 is locked away, so row 0 can reach 30 and no further however far the slider is dragged.
    const next = rebalance(panel(10, 40, 30, 20), 0, 95, lock(1, 2));
    expect(next[0].weight).toBe(30);
    expect(next[1].weight).toBe(40);
    expect(next[2].weight).toBe(30);
    expect(next[3].weight).toBe(0);
    expect(total(next)).toBe(100);
  });

  it("keeps the total exact when the split does not divide evenly", () => {
    const next = rebalance(panel(10, 30, 30, 30), 0, 13, lock(1));
    expect(next[1].weight).toBe(30);
    expect(total(next)).toBe(100);
  });

  it("never lands rounding drift on a locked row", () => {
    // A 3-way split of 1 cannot come out even, so somebody has to absorb the remainder.
    const next = rebalance(panel(10, 33, 33, 24), 0, 11, lock(1));
    expect(next[1].weight).toBe(33);
    expect(total(next)).toBe(100);
  });

  it("shares equally when every unlocked row sits at zero", () => {
    const next = rebalance(panel(60, 40, 0, 0), 0, 40, lock(1));
    expect(next[1].weight).toBe(40);
    expect(next[2].weight + next[3].weight).toBe(20);
    expect(total(next)).toBe(100);
  });

  it("behaves as before when nothing is locked", () => {
    expect(rebalance(panel(30, 30, 20, 20), 0, 50)).toEqual(
      rebalance(panel(30, 30, 20, 20), 0, 50, new Set()),
    );
  });
});
