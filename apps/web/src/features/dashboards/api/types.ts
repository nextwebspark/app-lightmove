/** A dashboard lives on one position or across the workspace; the API paths and query keys follow from it. */
export type DashboardScope = { kind: "project"; projectId: string } | { kind: "workspace" };

export type ChartType = "kpi" | "bar" | "column" | "pie" | "donut" | "line" | "area" | "stacked" | "table";

export type ChartShape = "KPI" | "CATEGORICAL" | "TIME_SERIES" | "MATRIX" | "RANKED";

/** S = a third of the row, M = half, L = the whole row. */
export type WidgetSize = "S" | "M" | "L";

export interface DashboardWidget {
  queryId: string;
  chart: ChartType;
  options: Record<string, string>;
  title: string | null;
  size: WidgetSize;
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
  noSum: boolean;
  staffOnly: boolean;
  defaultChart: ChartType;
  /** The types the shape allows before data is known; a result's `allowedCharts` may be narrower. */
  charts: ChartType[];
  options: ChartOptionDef[];
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
}
