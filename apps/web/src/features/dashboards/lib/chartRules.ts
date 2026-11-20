import { ICONS } from "../../../components/layout/Icon";
import type { ChartQueryDef, ChartShape, ChartType, WidgetSize } from "../api/types";

/** Mirrors the server's ChartRules: which chart types can honestly draw an answer of each shape. */
const SHAPE_CHARTS: Record<ChartShape, readonly ChartType[]> = {
  KPI: ["kpi"],
  CATEGORICAL: ["bar", "column", "pie", "donut", "table"],
  TIME_SERIES: ["line", "area", "column", "table"],
  MATRIX: ["stacked", "table"],
  RANKED: ["table"],
};

export const MAX_PIE_SLICES = 8;

/** A pie needs parts of one whole — never a median or a rate — and stops reading past eight slices. */
export function allowedCharts(shape: ChartShape, notAdditive: boolean, labelCount: number): ChartType[] {
  const charts = SHAPE_CHARTS[shape];
  if (shape === "CATEGORICAL" && (notAdditive || labelCount > MAX_PIE_SLICES)) {
    return charts.filter((chart) => chart !== "pie" && chart !== "donut");
  }
  return [...charts];
}

/** Every type a picker shows for a shape: the number alone for a single figure, all the rest otherwise. */
export function pickerCharts(shape: ChartShape): ChartType[] {
  return shape === "KPI" ? ["kpi"] : ["bar", "column", "pie", "donut", "line", "area", "stacked", "table"];
}

export const CHART_LABELS: Record<ChartType, string> = {
  kpi: "Number",
  bar: "Bar",
  column: "Column",
  pie: "Pie",
  donut: "Donut",
  line: "Line",
  area: "Area",
  stacked: "Stacked bar",
  table: "Table",
};

export const CHART_ICONS: Record<ChartType, string> = {
  kpi: "M4 9h16M4 15h16M10 3 8 21M16 3l-2 18",
  bar: "M3 6h12M3 12h18M3 18h8",
  column: "M6 20V10M12 20V4M18 20v-7",
  pie: "M21.2 15.9A10 10 0 1 1 8 2.8M22 12A10 10 0 0 0 12 2v10Z",
  donut: "M12 2a10 10 0 1 0 10 10M12 7a5 5 0 1 0 5 5M12 2v5M22 12h-5",
  line: "M3 17l6-6 4 4 8-8",
  area: "M3 17l6-6 4 4 8-8v13H3Z",
  stacked: "M3 6h7M10 6h8M3 12h11M14 12h6M3 18h5M8 18h9",
  table: ICONS.table,
};

export const SHAPE_LABELS: Record<ChartShape, string> = {
  KPI: "Single number",
  CATEGORICAL: "Breakdown",
  TIME_SERIES: "Over time",
  MATRIX: "Breakdown by two",
  RANKED: "Ranked list",
};

export const SIZE_LABELS: Record<WidgetSize, string> = { S: "Small", M: "Medium", L: "Full width" };

export const NEXT_SIZE: Record<WidgetSize, WidgetSize> = { S: "M", M: "L", L: "S" };

/** "Executives by …" completed with the chosen group, lower-cased: "Executives by level". */
export function defaultTitle(query: ChartQueryDef, options: Record<string, string>): string {
  const group = query.options.find((option) => option.key === "group");
  if (!query.label.endsWith(" …") || !group) return query.label;
  const chosen = group.choices.find((choice) => choice.value === options.group) ?? group.choices[0];
  return query.label.replace(" …", ` ${chosen.label.toLowerCase()}`);
}

/** The options a new chart of this query starts with. */
export function defaultOptions(query: ChartQueryDef): Record<string, string> {
  return Object.fromEntries(query.options.map((option) => [option.key, option.defaultValue]));
}

/** "By level · last 8 weeks · top 5": what a chart's options narrowed it to, under its title. */
export function optionSummary(query: ChartQueryDef, options: Record<string, string>): string {
  const parts: string[] = [];
  for (const option of query.options) {
    const value = options[option.key] ?? option.defaultValue;
    const choice = option.choices.find((candidate) => candidate.value === value);
    if (!choice) continue;
    if (option.key === "group") parts.push(`By ${choice.label.toLowerCase()}`);
    if (option.key === "range") parts.push(choice.label.toLowerCase());
    if (option.key === "top" && value !== "all") parts.push(choice.label.toLowerCase());
  }
  const summary = parts.join(" · ");
  return summary.charAt(0).toUpperCase() + summary.slice(1);
}
