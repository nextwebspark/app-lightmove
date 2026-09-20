import { describe, expect, it } from "vitest";
import { forWire, identify, moveRow, toggle } from "./competencyRows";

const rows = () =>
  identify([
    { name: "Controls", description: null, weight: 40 },
    { name: "M&A", description: null, weight: 35 },
    { name: "Treasury", description: null, weight: 25 },
  ]);

describe("identify / forWire", () => {
  it("gives every row its own id and takes it away again", () => {
    const identified = identify([
      { name: "A", description: null, weight: 50 },
      { name: "B", description: null, weight: 50 },
    ]);
    expect(new Set(identified.map((row) => row.id)).size).toBe(2);
    expect(forWire(identified)).toEqual([
      { name: "A", description: null, weight: 50 },
      { name: "B", description: null, weight: 50 },
    ]);
  });
});

describe("moveRow", () => {
  it("drops a row where the one it landed on was", () => {
    const before = rows();
    const after = moveRow(before, before[2].id, before[0].id);
    expect(after.map((row) => row.name)).toEqual(["Treasury", "Controls", "M&A"]);
  });

  it("moves downward as well as up", () => {
    const before = rows();
    const after = moveRow(before, before[0].id, before[2].id);
    expect(after.map((row) => row.name)).toEqual(["M&A", "Treasury", "Controls"]);
  });

  it("carries the weights with the rows rather than the positions", () => {
    const before = rows();
    const after = moveRow(before, before[2].id, before[0].id);
    expect(after[0].weight).toBe(25);
    expect(after.reduce((sum, row) => sum + row.weight, 0)).toBe(100);
  });

  it("leaves the list alone when the drop goes nowhere", () => {
    const before = rows();
    expect(moveRow(before, before[1].id, before[1].id)).toBe(before);
    expect(moveRow(before, "gone", before[0].id)).toBe(before);
  });
});

describe("toggle", () => {
  it("locks, then unlocks, and never touches the set it was given", () => {
    const empty: ReadonlySet<string> = new Set();
    const locked = toggle(empty, "a");
    expect([...locked]).toEqual(["a"]);
    expect([...toggle(locked, "a")]).toEqual([]);
    expect(empty.size).toBe(0);
  });

  it("keeps a lock on one row when another is locked", () => {
    expect([...toggle(toggle(new Set(), "a"), "b")].sort()).toEqual(["a", "b"]);
  });
});
