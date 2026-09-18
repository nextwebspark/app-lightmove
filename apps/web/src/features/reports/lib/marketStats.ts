import type { MarketCell, ReportMarket, SeniorityLevel, SliceExecutive } from "../api/types";
import { percent } from "./figures";
import { rampStop } from "./ramp";

export interface HeatRow {
  level: SeniorityLevel;
  /** `stop` is the cell's place on the sequential ramp, 0–4. */
  cells: { sector: string; level: SeniorityLevel; count: number; stop: number }[];
}

export interface MarketStats {
  rows: HeatRow[];
  /** The sectors the matrix draws — the ones somebody is actually placed in. */
  sectors: string[];
  emptyCells: number;
  totalCells: number;
  boardTotal: number;
  /** Null while no executive sits in any cell. */
  deepest: MarketCell | null;
  /** Executives with both a sector and a seniority — the ones the matrix can place. */
  placed: number;
  /** Executives with a place on file, hubs and elsewhere together. */
  located: number;
  topHubsPct: number;
  topHubs: string[];
}

export const TOP_HUBS = 3;

export function marketStats(market: ReportMarket): MarketStats {
  const countOf = (sector: string, level: SeniorityLevel) =>
    market.cells.find((c) => c.sector === sector && c.level === level)?.count ?? 0;
  const maxCell = Math.max(...market.cells.map((c) => c.count), 1);
  // Both axes carry only what somebody is placed at: a single hatched cell says "nobody in this
  // pocket yet", but a whole row or column of them says nothing.
  const holdsSomebody = (key: string, axisOf: (cell: MarketCell) => string) =>
    market.cells.some((cell) => axisOf(cell) === key && cell.count > 0);
  const levels = market.levels.filter((level) => holdsSomebody(level, (cell) => cell.level));
  const sectors = market.sectors.filter((sector) => holdsSomebody(sector, (cell) => cell.sector));
  const rows: HeatRow[] = levels.map((level) => ({
    level,
    cells: sectors.map((sector) => {
      const count = countOf(sector, level);
      return { sector, level, count, stop: rampStop(count, maxCell) };
    }),
  }));
  const totalCells = sectors.length * levels.length;
  const deepest = market.cells.reduce<MarketCell | null>((best, cell) => (cell.count > (best?.count ?? 0) ? cell : best), null);
  const located = market.hubs.reduce((sum, hub) => sum + hub.count, 0) + market.elsewhere;
  const topHubs = [...market.hubs].sort((a, b) => b.count - a.count).slice(0, TOP_HUBS);
  return {
    rows,
    sectors,
    emptyCells: totalCells - market.cells.filter((c) => c.count > 0).length,
    totalCells,
    boardTotal: market.cells.filter((c) => c.level === "Board").reduce((sum, c) => sum + c.count, 0),
    deepest,
    placed: market.cells.reduce((sum, c) => sum + c.count, 0),
    located,
    topHubsPct: percent(
      topHubs.reduce((sum, hub) => sum + hub.count, 0),
      located,
    ),
    topHubs: topHubs.map((hub) => hub.country),
  };
}

export interface SliceInterest {
  interested: number;
  passive: number;
  closed: number;
}

/** A pocket's listed executives by where the mandate has got to with them: yes, not yet, and a closed door. */
export function sliceInterest(executives: Pick<SliceExecutive, "status">[]): SliceInterest {
  return {
    interested: executives.filter((e) => e.status === "interested").length,
    closed: executives.filter((e) => e.status === "notInterested" || e.status === "offLimits" || e.status === "outOfScope").length,
    passive: executives.filter((e) => e.status === "identified" || e.status === "contacted" || e.status === "engaged").length,
  };
}
