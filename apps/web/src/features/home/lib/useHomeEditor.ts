import { useState } from "react";
import type { WidgetLayout } from "../../dashboards/api/types";
import type { ChartDraft } from "../../dashboards/components/ChartLibraryDrawer";
import { clampSize, compact, limitsFor, place, startingLayout } from "../../dashboards/lib/gridLayout";
import { chartCount, MAX_HOME_CHARTS, relaid, settled, type ChartTile, type HomeLayout, type HomeTile } from "./homeLayout";

/** A tile being edited carries a key of its own, so removing one never hands its place in the grid to the next. */
type KeyedTile = HomeTile & { key?: string };

let keySequence = 0;

function keyed(layout: HomeLayout): HomeLayout {
  return {
    ...layout,
    tiles: layout.tiles.map((tile: KeyedTile) => (tile.key ? tile : { ...tile, key: `tile-${++keySequence}` })),
  };
}

export function tileKey(tile: HomeTile, index: number): string {
  return (tile as KeyedTile).key ?? `${tile.kind}-${index}`;
}

/**
 * Home's edit mode: a draft of the layout that every move, resize, chart and option changes, kept only when saved.
 * `commitChart` answers false when Home already holds as many charts as it may.
 */
export function useHomeEditor(saved: HomeLayout) {
  const [draft, setDraft] = useState<HomeLayout | null>(null);
  const update = (next: HomeLayout) => setDraft(keyed(next));

  return {
    editing: draft !== null,
    layout: draft ?? saved,
    draft,
    start: () => update(saved),
    stop: () => setDraft(null),
    update,
    relayout: (layouts: WidgetLayout[]) => draft && setDraft(relaid(draft, layouts)),
    remove: (index: number) =>
      draft && setDraft(settled({ ...draft, tiles: draft.tiles.filter((_, other) => other !== index) })),
    commitChart: (chart: ChartDraft): boolean => {
      if (!draft) return true;
      const tile: ChartTile = {
        kind: "CHART",
        queryId: chart.query.id,
        chart: chart.chart,
        options: chart.options,
        title: chart.title.trim() || null,
        layout:
          chart.index === null
            ? startingLayout(draft.tiles.map((other) => other.layout), chart.chart)
            : clampSize(draft.tiles[chart.index].layout, limitsFor(chart.chart)),
      };
      if (chart.index === null) {
        if (chartCount(draft) >= MAX_HOME_CHARTS) return false;
        update({ ...draft, tiles: [...draft.tiles, tile] });
        return true;
      }
      const replaced = draft.tiles[chart.index] as KeyedTile;
      const tiles = draft.tiles.map((other, index) => (index === chart.index ? { ...tile, key: replaced.key } : other));
      update(relaid({ ...draft, tiles }, compact(place(tiles.map((other) => other.layout), chart.index, tile.layout))));
      return true;
    },
  };
}
