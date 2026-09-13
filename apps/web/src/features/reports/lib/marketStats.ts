import { SENIORITY_LEVELS, type MarketCell, type ReportMarket, type SeniorityLevel } from "../api/types";
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
  deepest: MarketCell;
  executivesMapped: number;
  hubTotal: number;
  topHubsPct: number;
  topHubs: string[];
}

export const TOP_HUBS = 3;

export function marketStats(market: ReportMarket): MarketStats {
  const countOf = (sector: string, level: SeniorityLevel) =>
    market.cells.find((c) => c.sector === sector && c.level === level)?.count ?? 0;
  const maxCell = Math.max(...market.cells.map((c) => c.count), 1);
  const rows: HeatRow[] = SENIORITY_LEVELS.map((level) => ({
    level,
    cells: market.sectors.map((sector) => {
      const count = countOf(sector, level);
      return { sector, level, count, intensity: count / maxCell };
    }),
  }));
  const totalCells = market.sectors.length * SENIORITY_LEVELS.length;
  const deepest = market.cells.reduce((a, b) => (b.count > a.count ? b : a));
  const hubTotal = market.hubs.reduce((sum, hub) => sum + hub.count, 0);
  const topHubs = [...market.hubs].sort((a, b) => b.count - a.count).slice(0, TOP_HUBS);
  return {
    rows,
    maxCell,
    emptyCells: totalCells - market.cells.filter((c) => c.count > 0).length,
    totalCells,
    boardTotal: market.cells.filter((c) => c.level === "Board").reduce((sum, c) => sum + c.count, 0),
    deepest,
    executivesMapped: market.cells.reduce((sum, c) => sum + c.count, 0),
    hubTotal,
    topHubsPct: percent(
      topHubs.reduce((sum, hub) => sum + hub.count, 0),
      hubTotal,
    ),
    topHubs: topHubs.map((hub) => hub.city),
  };
}

export interface SliceInterest {
  interested: number;
  passive: number;
  offLimits: number;
}

/**
 * The pocket's own breakdown from its listed executives. Where a slice carries no list yet, the
 * report has no interest figure for it and says so with zeros rather than inventing a split.
 */
export function sliceInterest(executives: { status: string }[]): SliceInterest {
  return {
    interested: executives.filter((e) => e.status === "interested").length,
    passive: executives.filter((e) => e.status === "passive" || e.status === "verified").length,
    offLimits: executives.filter((e) => e.status === "offlimits").length,
  };
}
