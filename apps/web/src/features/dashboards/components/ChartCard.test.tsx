import { render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { ChartAnswer, ChartQueryDef, ChartResult, DashboardWidget } from "../api/types";
import { ChartCard } from "./ChartCard";

vi.mock("../../auth/AuthProvider", () => ({
  useAuth: () => ({ user: { id: "u1", workspace: { roles: ["ADMIN"], mode: "COMPANY" } } }),
}));

const widget: DashboardWidget = { queryId: "positionsBy", chart: "bar", options: {}, title: "By position", layout: null };

const result = (labels: string[], values: number[]): ChartResult => ({
  queryId: "positionsBy",
  shape: "CATEGORICAL",
  allowedCharts: ["bar", "column", "table"],
  options: {},
  dimension: "Position",
  measure: "Executives",
  unit: "",
  labels,
  values,
  series: [],
  suppressed: [],
  kpi: null,
  columns: [],
  rows: [],
  note: null,
  percent: false,
  truncated: false,
  folded: false,
});

const scope = { kind: "workspace" } as const;

const query: ChartQueryDef = {
  id: "positionsBy",
  category: "Pipeline",
  label: "Executives by position",
  description: "Executives mapped on each open position",
  shape: "CATEGORICAL",
  notAdditive: false,
  defaultChart: "bar",
  charts: ["bar", "column", "pie", "donut", "table"],
  options: [],
};

describe("ChartCard", () => {
  afterEach(() => vi.restoreAllMocks());

  it("says the chart couldn't load, not that it was retired, when the library itself failed to load", () => {
    render(<ChartCard scope={scope} widget={widget} query={undefined} answer={undefined} isPending={false} isError />);
    expect(screen.getByText(/Couldn't load this chart/)).toBeInTheDocument();
    expect(screen.queryByText(/no longer available/)).not.toBeInTheDocument();
  });

  it("still says a retired chart is no longer available", () => {
    const answer: ChartAnswer = { queryId: "positionsBy", result: null, code: "CHART_QUERY_NOT_FOUND" };
    render(<ChartCard scope={scope} widget={widget} query={undefined} answer={answer} isPending={false} isError={false} />);
    expect(screen.getByText(/no longer available/)).toBeInTheDocument();
  });

  it("names a chart type that no longer fits in words, never by its token", () => {
    const answer: ChartAnswer = { queryId: "positionsBy", result: result(["A"], [1]), code: null };
    render(
      <ChartCard scope={scope} widget={{ ...widget, chart: "pie" }} query={query} answer={answer}
                 isPending={false} isError={false} />,
    );
    expect(screen.getByText(/no longer fits the Pie chart type/)).toBeInTheDocument();
  });

  it("draws every group when two carry the same label", () => {
    const warn = vi.spyOn(console, "error").mockImplementation(() => {});
    const answer: ChartAnswer = { queryId: "positionsBy", result: result(["CFO", "CFO", "COO"], [4, 2, 1]), code: null };
    render(<ChartCard scope={scope} widget={widget} query={query} answer={answer} isPending={false} isError={false} />);
    expect(screen.getAllByText("CFO")).toHaveLength(2);
    expect(warn).not.toHaveBeenCalledWith(expect.stringContaining("same key"), expect.anything(), expect.anything());
  });
});
