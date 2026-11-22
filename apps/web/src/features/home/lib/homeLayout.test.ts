import { afterEach, describe, expect, it } from "vitest";
import {
  DEFAULT_HOME_LAYOUT,
  forgetLegacyLayout,
  hasSection,
  isDefaultLayout,
  legacyLayout,
  tileLimits,
  withSection,
} from "./homeLayout";

const owner = { userId: "u1", workspaceId: "w1" };

describe("homeLayout", () => {
  afterEach(() => localStorage.clear());

  it("takes a section off Home and lets the rest rise, then brings it back at the foot at its usual size", () => {
    const off = withSection(DEFAULT_HOME_LAYOUT, "TODAY", false);
    expect(hasSection(off, "TODAY")).toBe(false);
    expect(off.tiles.find((tile) => tile.kind === "POSITIONS")?.layout).toEqual({ x: 8, y: 0, w: 4, h: 6 });

    const back = withSection(off, "TODAY", true);
    expect(back.tiles.at(-1)).toEqual({ kind: "TODAY", layout: expect.objectContaining({ w: 4, h: 5 }) });
    expect(withSection(back, "TODAY", true)).toBe(back);
  });

  it("keeps Needs you readable: it cannot be made smaller than half the page", () => {
    expect(tileLimits(DEFAULT_HOME_LAYOUT.tiles[0])).toMatchObject({ minW: 5, minH: 6 });
    expect(tileLimits({ kind: "CHART", queryId: "x", chart: "bar", options: {}, title: null, layout: { x: 0, y: 0, w: 6, h: 7 } }))
      .toMatchObject({ minW: 3, minH: 5 });
  });

  it("carries a layout kept on this browser before Home was saved on the server", () => {
    localStorage.setItem(
      "lm-home:u1:w1",
      JSON.stringify({
        sections: [{ key: "positions", on: true }, { key: "today", on: false }, { key: "charts", on: true }],
        kinds: { ATTENTION: false },
        charts: ["openPositions"],
      }),
    );

    const carried = legacyLayout(owner);
    expect(carried?.tiles.map((tile) => tile.kind)).toEqual(["NEEDS", "POSITIONS", "CHART"]);
    expect(carried?.kinds.ATTENTION).toBe(false);

    forgetLegacyLayout(owner);
    expect(legacyLayout(owner)).toBeNull();
  });

  it("knows the default when it sees it", () => {
    expect(isDefaultLayout(structuredClone(DEFAULT_HOME_LAYOUT))).toBe(true);
    // The same layout rebuilt with its keys in another order, as a tile spread or the server's answer leaves it.
    const reordered = {
      kinds: { ATTENTION: true, PREPARATION: true, FOLLOW_UP: true, NEEDS_REPLY: true },
      tiles: DEFAULT_HOME_LAYOUT.tiles.map(({ layout, ...rest }) => ({
        key: "tile-9",
        layout: { h: layout.h, w: layout.w, y: layout.y, x: layout.x },
        ...rest,
      })),
    };
    expect(isDefaultLayout(reordered)).toBe(true);
    expect(isDefaultLayout(withSection(DEFAULT_HOME_LAYOUT, "TODAY", false))).toBe(false);
  });
});
