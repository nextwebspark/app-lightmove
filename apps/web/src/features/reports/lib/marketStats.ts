import type { MarketCell, ReportMarket, SeniorityLevel, SliceExecutive } from "../api/types";
import { percent } from "./figures";

export interface HeatRow {
  level: SeniorityLevel;
  cells: { sector: string; level: SeniorityLevel; count: number; intensity: number }[];
}

export interface MarketStats {
  rows: HeatRow[];
  maxCell: number;
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
  const rows: HeatRow[] = market.levels.map((level) => ({
    level,
    cells: market.sectors.map((sector) => {
      const count = countOf(sector, level);
      return { sector, level, count, intensity: count / maxCell };
    }),
  }));
  const totalCells = market.sectors.length * market.levels.length;
  const deepest = market.cells.reduce<MarketCell | null>((best, cell) => (cell.count > (best?.count ?? 0) ? cell : best), null);
  const located = market.hubs.reduce((sum, hub) => sum + hub.count, 0) + market.elsewhere;
  const topHubs = [...market.hubs].sort((a, b) => b.count - a.count).slice(0, TOP_HUBS);
  return {
    rows,
    maxCell,
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
