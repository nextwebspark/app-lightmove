/** A dashboard lives on one position or across the workspace; the API paths and query keys follow from it. */
export type DashboardScope = { kind: "project"; projectId: string } | { kind: "workspace" };

export type ChartType = "kpi" | "bar" | "column" | "pie" | "donut" | "line" | "area" | "stacked" | "table";

export type ChartShape = "KPI" | "CATEGORICAL" | "TIME_SERIES" | "MATRIX" | "RANKED";

/** A chart's place on the twelve-column grid: column `x`, row `y`, `w` columns wide, `h` rows tall. */
export interface WidgetLayout {
  x: number;
  y: number;
  w: number;
  h: number;
}

export interface DashboardWidget {
  queryId: string;
  chart: ChartType;
  options: Record<string, string>;
  title: string | null;
  /** Null only on a chart saved before the grid; the screen flows it into place. */
  layout: WidgetLayout | null;
  /** The S/M/L width a chart saved before the grid was given; null once it has a layout. */
  size?: string | null;
}

export interface Dashboard {
  id: string;
  name: string;
  shared: boolean;
  /** Theirs to change; a teammate's shared dashboard is read-only. */
  owned: boolean;
  widgets: DashboardWidget[];
  version: number;
  updatedAt: string;
}

export interface SaveDashboard {
  name: string;
  shared: boolean;
  widgets: DashboardWidget[];
  version: number | null;
}

export interface ChartChoice {
  value: string;
  label: string;
}

export interface ChartOptionDef {
  key: "group" | "range" | "top";
  label: string;
  choices: ChartChoice[];
  defaultValue: string;
}

export interface ChartQueryDef {
  id: string;
  category: string;
  /** May end in " …", completed with the chosen group's name. */
  label: string;
  description: string;
  shape: ChartShape;
  /** Its parts are no whole (a median, a rate): never a pie. */
  notAdditive: boolean;
  defaultChart: ChartType;
  /** The types the shape allows before data is known; a result's `allowedCharts` may be narrower. */
  charts: ChartType[];
  options: ChartOptionDef[];
}

/** One chart's answer from a dashboard's batch: the result, or the code of why it has none. */
export interface ChartAnswer {
  queryId: string;
  result: ChartResult | null;
  code: string | null;
}

export interface ChartSeries {
  name: string;
  values: number[];
}

export interface ChartKpi {
  value: string;
  unit: string;
  sub: string;
  alarm: boolean;
}

export interface ChartResult {
  queryId: string;
  shape: ChartShape;
  allowedCharts: ChartType[];
  options: Record<string, string>;
  dimension: string;
  measure: string;
  unit: string;
  labels: string[];
  values: number[];
  series: ChartSeries[];
  /** Labels held back because too few people stand behind them; their values arrive zeroed. */
  suppressed: boolean[];
  kpi: ChartKpi | null;
  columns: string[];
  rows: string[][];
  note: string | null;
  /** A stacked bar draws shares of each row, not counts. */
  percent: boolean;
  /** The answer was cut to the bucket cap. */
  truncated: boolean;
  /** The smallest groups were folded into Other; a median's tail is left off instead. */
  folded: boolean;
}
