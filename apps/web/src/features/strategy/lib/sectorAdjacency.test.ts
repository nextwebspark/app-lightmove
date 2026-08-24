import { describe, expect, it } from "vitest";
import { adjacencyEntries, adjacentTo } from "./sectorAdjacency";
import type { SectorGroup } from "../api/types";

const groupNamed = (name: string): SectorGroup => ({ name, industries: [] });

describe("sector adjacency", () => {
  it("is symmetric", () => {
    const map = new Map(adjacencyEntries());

    for (const [group, neighbours] of map) {
      for (const neighbour of neighbours) {
        // A one-way edge puts the chip under one sector and not the other, which reads as the
        // suggestions having an opinion they do not have.
        expect(map.get(neighbour), `${group} → ${neighbour}`).toContain(group);
      }
    }
  });

  it("names only sectors it also defines, and never itself", () => {
    const names = new Set(adjacencyEntries().map(([group]) => group));

    for (const [group, neighbours] of adjacencyEntries()) {
      expect(neighbours).not.toContain(group);
      expect(new Set(neighbours).size).toBe(neighbours.length);
      neighbours.forEach((neighbour) => expect(names).toContain(neighbour));
    }
  });

  it("every sector suggests something", () => {
    for (const [group, neighbours] of adjacencyEntries()) {
      expect(neighbours, group).not.toHaveLength(0);
    }
  });

  it("drops a neighbour the server no longer groups", () => {
    const known = new Map([["Technology", groupNamed("Technology")]]);

    // The taxonomy is the server's and this file is not; a rename there must cost a suggestion, not
    // render a chip that selects nothing.
    expect(adjacentTo(groupNamed("Technology"), known)).toEqual([]);
  });
});
