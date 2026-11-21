import { act, renderHook } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { ChartQueryDef } from "../../dashboards/api/types";
import { DEFAULT_HOME_LAYOUT, MAX_HOME_CHARTS } from "./homeLayout";
import { tileKey, useHomeEditor } from "./useHomeEditor";

const query = { id: "pipelineByStage", defaultChart: "bar", options: [] } as unknown as ChartQueryDef;
const chartDraft = (index: number | null = null) => ({ query, chart: "bar" as const, options: {}, title: "", index });

describe("useHomeEditor", () => {
  it("keeps each tile's key when a tile before it is taken off", () => {
    const { result } = renderHook(() => useHomeEditor(DEFAULT_HOME_LAYOUT));
    act(() => result.current.start());
    const keys = result.current.layout.tiles.map(tileKey);

    act(() => result.current.remove(1));

    expect(result.current.layout.tiles.map(tileKey)).toEqual([keys[0], ...keys.slice(2)]);
  });

  it("adds a chart at the foot, and refuses one past Home's limit", () => {
    const { result } = renderHook(() => useHomeEditor(DEFAULT_HOME_LAYOUT));
    act(() => result.current.start());
    let added = true;
    for (let i = 0; i < MAX_HOME_CHARTS; i++) act(() => void (added = result.current.commitChart(chartDraft())));

    expect(added).toBe(false);
    expect(result.current.layout.tiles.filter((tile) => tile.kind === "CHART")).toHaveLength(MAX_HOME_CHARTS);
  });

  it("drops the draft on stop, back to the saved layout", () => {
    const { result } = renderHook(() => useHomeEditor(DEFAULT_HOME_LAYOUT));
    act(() => result.current.start());
    act(() => result.current.remove(1));
    act(() => result.current.stop());

    expect(result.current.editing).toBe(false);
    expect(result.current.layout).toBe(DEFAULT_HOME_LAYOUT);
  });
});
