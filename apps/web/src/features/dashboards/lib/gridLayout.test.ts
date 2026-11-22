import { describe, expect, it } from "vitest";
import type { ChartType, WidgetLayout } from "../api/types";
import { clampSize, compact, limitsFor, moveDown, place, settledWidgets, startingLayout, withLayouts } from "./gridLayout";

const at = (x: number, y: number, w: number, h: number): WidgetLayout => ({ x, y, w, h });

describe("gridLayout", () => {
  it("slides a chart straight up into empty rows, keeping its column", () => {
    expect(compact([at(0, 0, 4, 3), at(6, 9, 6, 4)])).toEqual([at(0, 0, 4, 3), at(6, 0, 6, 4)]);
  });

  it("keeps a shrunk chart in its own row when something sits above it", () => {
    const shrunk = place([at(0, 0, 12, 4), at(0, 4, 12, 6)], 1, at(0, 4, 4, 6));
    expect(compact(shrunk)[1]).toEqual(at(0, 4, 4, 6));
  });

  it("pushes the chart a drop lands on down below it", () => {
    const moved = place([at(0, 0, 6, 4), at(6, 0, 6, 4)], 1, at(0, 0, 6, 4));
    expect(moved).toEqual([at(0, 4, 6, 4), at(0, 0, 6, 4)]);
  });

  it("holds a resize within the chart type's limits and the grid", () => {
    expect(clampSize(at(10, 0, 1, 1), limitsFor("bar"))).toEqual(at(9, 0, 3, 5));
    expect(clampSize(at(0, 0, 20, 40), limitsFor("kpi"))).toEqual(at(0, 0, 12, 24));
  });

  it("puts a new chart beside the last row when it fits there", () => {
    expect(startingLayout([at(0, 0, 6, 7)], "bar")).toEqual(at(6, 0, 6, 7));
    expect(startingLayout([at(0, 0, 12, 4)], "kpi")).toEqual(at(0, 4, 3, 3));
  });

  it("flows charts saved before the grid left to right by their old size", () => {
    const legacy = [
      { chart: "kpi" as ChartType, layout: null, size: "S" },
      { chart: "bar" as ChartType, layout: null, size: "M" },
      { chart: "table" as ChartType, layout: null, size: "L" },
    ];
    expect(withLayouts(legacy).map((widget) => widget.layout)).toEqual([at(0, 0, 4, 3), at(4, 0, 6, 7), at(0, 7, 12, 7)]);
  });

  it("never lifts a chart through another into a gap above it", () => {
    const rects = [at(0, 0, 6, 1), at(6, 0, 6, 3), at(0, 3, 12, 5), at(0, 8, 6, 2)];
    expect(compact(rects)).toEqual(rects);
  });

  it("moves a chart down by trading places with the one below it", () => {
    expect(moveDown([at(0, 0, 6, 5), at(0, 5, 6, 3)], 0)).toEqual([at(0, 3, 6, 5), at(0, 0, 6, 3)]);
    expect(moveDown([at(0, 0, 6, 5)], 0)).toEqual([at(0, 0, 6, 5)]);
  });

  it("sends a chart saved before the grid back with a place", () => {
    const widgets = settledWidgets([{ chart: "kpi" as ChartType, layout: null, size: "L" }]);
    expect(widgets[0]).toMatchObject({ layout: at(0, 0, 12, 3), size: null });
  });
});

describe("dragging a chart down", () => {
  it("trades places with the chart it lands on, and keeps the trade once everything settles", () => {
    const dropped = compact(place([at(0, 0, 12, 4), at(0, 4, 12, 4)], 0, at(0, 4, 12, 4)));
    expect(dropped).toEqual([at(0, 4, 12, 4), at(0, 0, 12, 4)]);
  });

  it("pushes the chart below when there is no room for it above", () => {
    const dropped = compact(place([at(0, 0, 12, 4), at(0, 4, 12, 4)], 0, at(0, 2, 12, 4)));
    expect(dropped).toEqual([at(0, 0, 12, 4), at(0, 4, 12, 4)]);
  });

  it("leaves a chart beside the drop where it is", () => {
    const dropped = compact(place([at(0, 0, 6, 4), at(6, 0, 6, 4), at(0, 4, 6, 4)], 0, at(0, 4, 6, 4)));
    expect(dropped).toEqual([at(0, 4, 6, 4), at(6, 0, 6, 4), at(0, 0, 6, 4)]);
  });
});
