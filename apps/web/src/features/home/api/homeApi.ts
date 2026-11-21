import { request } from "../../../lib/apiClient";
import { TASKS_KEY_PREFIX } from "../../tasks/api/tasksApi";
import type { MyTasksView } from "../../tasks/api/types";
import type { ChartType, DashboardScope, WidgetLayout } from "../../dashboards/api/types";
import { DEFAULT_HOME_LAYOUT, type HomeLayout, type HomeTile, type NeedsKind } from "../lib/homeLayout";

/** Home's charts read the workspace's library, never a position's. */
export const WORKSPACE_CHARTS: DashboardScope = { kind: "workspace" };
import type { ConversationRow, ConversationScope } from "../../outreach/api/conversationApi";

/** One row of Home's outreach queue: a position's conversation row, and the position it belongs to. */
export interface MyConversationRow {
  projectId: string;
  positionTitle: string;
  row: ConversationRow;
}

/** Rows in the queue's own order; `truncated` when more wait than were sent. */
export interface MyOutreachQueue {
  rows: MyConversationRow[];
  truncated: boolean;
}

/** Home's task reads: under every task write's invalidation, and apart from My tasks' infinite lists. */
export const HOME_TASKS_KEY = (view: MyTasksView) => [...TASKS_KEY_PREFIX, "home", view] as const;

export const MY_OUTREACH_NEEDS_KEY = (who: ConversationScope) => ["outreach", "me", "needs", who] as const;

export function getMyOutreachNeeds(who: ConversationScope, signal?: AbortSignal): Promise<MyOutreachQueue> {
  return request<MyOutreachQueue>(`/me/outreach/needs?who=${who}`, { signal });
}

/** A tile as the server keeps it: kind in capitals, a chart's fields beside its place. */
export interface HomeTileBody {
  kind: "NEEDS" | "TODAY" | "POSITIONS" | "CHART";
  layout: WidgetLayout;
  queryId?: string | null;
  chart?: ChartType | null;
  options?: Record<string, string> | null;
  title?: string | null;
}

export interface HomeLayoutBody {
  tiles: HomeTileBody[];
  needs: Partial<Record<NeedsKind, boolean>>;
  landing: "HOME" | "POSITIONS";
  /** The version the layout was read at; a save sends back the one it opened, null when it opened the default. */
  version: number | null;
}

/** The person's saved Home and the version it was read at. */
export interface SavedHome {
  layout: HomeLayout;
  version: number;
}

export const HOME_LAYOUT_KEY = ["home", "layout"] as const;

export function toHomeLayout(body: HomeLayoutBody): HomeLayout {
  const tiles = body.tiles.map((tile): HomeTile =>
    tile.kind === "CHART"
      ? {
          kind: "CHART",
          layout: tile.layout,
          queryId: tile.queryId ?? "",
          chart: tile.chart ?? "kpi",
          options: tile.options ?? {},
          title: tile.title ?? null,
        }
      : { kind: tile.kind, layout: tile.layout },
  );
  return {
    tiles,
    kinds: { ...DEFAULT_HOME_LAYOUT.kinds, ...body.needs },
    landing: body.landing === "POSITIONS" ? "positions" : "home",
  };
}

export function toHomeLayoutBody(layout: HomeLayout, version: number | null): HomeLayoutBody {
  return {
    tiles: layout.tiles.map((tile) =>
      tile.kind === "CHART"
        ? { kind: "CHART", layout: tile.layout, queryId: tile.queryId, chart: tile.chart, options: tile.options, title: tile.title }
        : { kind: tile.kind, layout: tile.layout },
    ),
    needs: layout.kinds,
    landing: layout.landing === "positions" ? "POSITIONS" : "HOME",
    version,
  };
}

function toSavedHome(body: HomeLayoutBody): SavedHome {
  return { layout: toHomeLayout(body), version: body.version ?? 0 };
}

/** The caller's saved Home in this workspace; null until they first save one. */
export async function getHomeLayout(signal?: AbortSignal): Promise<SavedHome | null> {
  const { layout } = await request<{ layout: HomeLayoutBody | null }>("/me/home", { signal });
  return layout ? toSavedHome(layout) : null;
}

/** Saves over the version it was opened at; a save since then elsewhere answers HOME_LAYOUT_STALE. */
export async function saveHomeLayout(layout: HomeLayout, version: number | null): Promise<SavedHome> {
  const saved = await request<{ layout: HomeLayoutBody }>("/me/home", {
    method: "PUT",
    body: toHomeLayoutBody(layout, version),
  });
  return toSavedHome(saved.layout);
}

/** Forget the saved Home: the person draws the default again, and follows it as it changes. */
export async function resetHomeLayout(): Promise<void> {
  await request<void>("/me/home", { method: "DELETE" });
}
