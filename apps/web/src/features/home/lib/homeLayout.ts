import type { ChartType, WidgetLayout } from "../../dashboards/api/types";
import { compact, GRID_COLUMNS, limitsFor, nextSlot, type SizeLimits } from "../../dashboards/lib/gridLayout";

/** Home's own sections; Needs you is always there, the others may be taken off. */
export type HomeSection = "NEEDS" | "TODAY" | "POSITIONS";

/** The outreach groups Needs you can carry; the person's own tasks are always there. */
export type NeedsKind = "NEEDS_REPLY" | "FOLLOW_UP" | "PREPARATION" | "ATTENTION";

export type Landing = "home" | "positions";

export interface SectionTile {
  kind: HomeSection;
  layout: WidgetLayout;
}

/** A chart from the workspace's dashboard library, kept as a dashboard keeps one. */
export interface ChartTile {
  kind: "CHART";
  layout: WidgetLayout;
  queryId: string;
  chart: ChartType;
  options: Record<string, string>;
  title: string | null;
}

export type HomeTile = SectionTile | ChartTile;

export interface HomeLayout {
  /** Every tile on Home, each at its place on the dashboard's twelve-column grid. */
  tiles: HomeTile[];
  kinds: Record<NeedsKind, boolean>;
  landing: Landing;
}

export const MAX_HOME_CHARTS = 12;

const chartTile = (queryId: string, x: number, y: number): ChartTile => ({
  kind: "CHART",
  layout: { x, y, w: 4, h: 3 },
  queryId,
  chart: "kpi",
  options: {},
  title: null,
});

export const DEFAULT_HOME_LAYOUT: HomeLayout = {
  tiles: [
    { kind: "NEEDS", layout: { x: 0, y: 0, w: 8, h: 11 } },
    { kind: "TODAY", layout: { x: 8, y: 0, w: 4, h: 5 } },
    { kind: "POSITIONS", layout: { x: 8, y: 5, w: 4, h: 6 } },
    chartTile("executivesEngaged", 0, 11),
    chartTile("positionsOffTrack", 4, 11),
    chartTile("openPositions", 8, 11),
  ],
  kinds: { NEEDS_REPLY: true, FOLLOW_UP: true, PREPARATION: true, ATTENTION: true },
  landing: "home",
};

const KINDS = Object.keys(DEFAULT_HOME_LAYOUT.kinds) as NeedsKind[];

/** The smallest each section still reads at, and the size it comes back at when switched on again. */
const SECTION_SIZES: Record<HomeSection, { minW: number; minH: number; w: number; h: number }> = {
  NEEDS: { minW: 5, minH: 6, w: 8, h: 11 },
  TODAY: { minW: 3, minH: 3, w: 4, h: 5 },
  POSITIONS: { minW: 3, minH: 4, w: 4, h: 6 },
};

export function tileLimits(tile: HomeTile): SizeLimits {
  if (tile.kind === "CHART") return limitsFor(tile.chart);
  const { minW, minH } = SECTION_SIZES[tile.kind];
  return { minW, minH, maxW: GRID_COLUMNS, maxH: 24 };
}

export function hasSection(layout: HomeLayout, section: HomeSection): boolean {
  return layout.tiles.some((tile) => tile.kind === section);
}

/** A section switched on goes to the first gap at the foot of Home, at its usual size; switched off, the rest settle. */
export function withSection(layout: HomeLayout, section: Exclude<HomeSection, "NEEDS">, on: boolean): HomeLayout {
  if (on === hasSection(layout, section)) return layout;
  if (!on) return settled({ ...layout, tiles: layout.tiles.filter((tile) => tile.kind !== section) });
  const { w, h } = SECTION_SIZES[section];
  const place = nextSlot(layout.tiles.map((tile) => tile.layout), w, h);
  return { ...layout, tiles: [...layout.tiles, { kind: section, layout: place }] };
}

export function settled(layout: HomeLayout): HomeLayout {
  const rects = compact(layout.tiles.map((tile) => tile.layout));
  return { ...layout, tiles: layout.tiles.map((tile, index) => ({ ...tile, layout: rects[index] })) };
}

export function relaid(layout: HomeLayout, layouts: WidgetLayout[]): HomeLayout {
  return { ...layout, tiles: layout.tiles.map((tile, index) => ({ ...tile, layout: layouts[index] })) };
}

export function chartCount(layout: HomeLayout): number {
  return layout.tiles.filter((tile) => tile.kind === "CHART").length;
}

function sameRect(left: WidgetLayout, right: WidgetLayout): boolean {
  return left.x === right.x && left.y === right.y && left.w === right.w && left.h === right.h;
}

function sameOptions(left: Record<string, string>, right: Record<string, string>): boolean {
  const keys = Object.keys(left);
  return keys.length === Object.keys(right).length && keys.every((key) => left[key] === right[key]);
}

function sameTile(left: HomeTile, right: HomeTile): boolean {
  if (left.kind !== right.kind || !sameRect(left.layout, right.layout)) return false;
  if (left.kind !== "CHART" || right.kind !== "CHART") return true;
  return left.queryId === right.queryId && left.chart === right.chart && left.title === right.title
    && sameOptions(left.options, right.options);
}

/** Field by field, never by serialising: the answer decides whether a save keeps a row or follows the default. */
export function sameLayout(left: HomeLayout, right: HomeLayout): boolean {
  return left.landing === right.landing
    && KINDS.every((kind) => left.kinds[kind] === right.kinds[kind])
    && left.tiles.length === right.tiles.length
    && left.tiles.every((tile, index) => sameTile(tile, right.tiles[index]));
}

export function isDefaultLayout(layout: HomeLayout): boolean {
  return sameLayout(layout, DEFAULT_HOME_LAYOUT);
}

/** Whose Home, in which workspace: one browser may hold several people, and one person several workspaces. */
export interface HomeOwner {
  userId: string;
  workspaceId: string;
}

const landingKeyOf = ({ userId, workspaceId }: HomeOwner) => `lm-landing:${userId}:${workspaceId}`;
/** Where Home's layout lived before it was kept on the server; read once, to carry it over. */
const legacyKeyOf = ({ userId, workspaceId }: HomeOwner) => `lm-home:${userId}:${workspaceId}`;

function readJson(key: string): unknown {
  try {
    const raw = localStorage.getItem(key);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

/**
 * Where to land after signing in: Home, unless the person chose Positions in this workspace. Sign-in decides before any
 * request, so the choice is remembered on this browser whenever Home reads or saves it.
 */
export function landingPath(owner: HomeOwner): string {
  const landing = readJson(landingKeyOf(owner)) ?? (readJson(legacyKeyOf(owner)) as { landing?: unknown } | null)?.landing;
  return landing === "positions" ? "/positions" : "/";
}

export function rememberLanding(owner: HomeOwner, landing: Landing): void {
  try {
    localStorage.setItem(landingKeyOf(owner), JSON.stringify(landing));
  } catch {
    // Blocked storage only costs the landing choice on this browser's next sign-in.
  }
}

/**
 * The layout someone kept on this browser before Home was saved with them: its sections and headline charts, laid
 * out as the default lays them, so their choice carries over on their first save. Null when there is none.
 */
export function legacyLayout(owner: HomeOwner): HomeLayout | null {
  const stored = readJson(legacyKeyOf(owner)) as {
    sections?: { key?: string; on?: boolean }[];
    kinds?: Partial<Record<NeedsKind, unknown>>;
    charts?: unknown[];
    landing?: unknown;
  } | null;
  if (!stored || typeof stored !== "object" || !Array.isArray(stored.sections)) return null;
  let layout: HomeLayout = { ...DEFAULT_HOME_LAYOUT, tiles: [DEFAULT_HOME_LAYOUT.tiles[0]] };
  for (const section of stored.sections) {
    if (section?.on === false) continue;
    if (section?.key === "today") layout = withSection(layout, "TODAY", true);
    if (section?.key === "positions") layout = withSection(layout, "POSITIONS", true);
  }
  const charts = (Array.isArray(stored.charts) ? stored.charts : [])
    .filter((id): id is string => typeof id === "string")
    .slice(0, MAX_HOME_CHARTS);
  for (const queryId of charts) {
    const place = nextSlot(layout.tiles.map((tile) => tile.layout), 4, 3);
    layout = { ...layout, tiles: [...layout.tiles, { ...chartTile(queryId, place.x, place.y), layout: place }] };
  }
  const kinds = { ...DEFAULT_HOME_LAYOUT.kinds };
  for (const kind of KINDS) {
    const own = stored.kinds?.[kind];
    if (typeof own === "boolean") kinds[kind] = own;
  }
  return { ...layout, kinds, landing: stored.landing === "positions" ? "positions" : "home" };
}

export function forgetLegacyLayout(owner: HomeOwner): void {
  try {
    localStorage.removeItem(legacyKeyOf(owner));
  } catch {
    // Nothing to forget.
  }
}
