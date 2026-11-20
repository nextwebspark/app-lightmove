import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import * as dashboardsApi from "../api/dashboardsApi";
import type { ChartQueryDef, ChartResult, Dashboard } from "../api/types";
import { WorkspaceDashboardsPage } from "./WorkspaceDashboardsPage";

vi.mock("../api/dashboardsApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/dashboardsApi")>()),
  listDashboards: vi.fn(),
  getChartLibrary: vi.fn(),
  runChartQuery: vi.fn(),
  getChartAnswers: vi.fn(),
  duplicateDashboard: vi.fn(),
  createDashboard: vi.fn(),
  updateDashboard: vi.fn(),
  deleteDashboard: vi.fn(),
}));

let mode: "COMPANY" | "AGENCY" = "COMPANY";
vi.mock("../../auth/AuthProvider", () => ({
  useAuth: () => ({ user: { id: "u1", workspace: { roles: ["ADMIN"], mode } } }),
}));

const positionsBy: ChartQueryDef = {
  id: "positionsBy",
  category: "Positions",
  label: "Open positions by …",
  description: "Open positions by health, stage, business unit or lead",
  shape: "CATEGORICAL",
  notAdditive: false,
  defaultChart: "donut",
  charts: ["bar", "column", "pie", "donut", "table"],
  options: [
    {
      key: "group",
      label: "Group by",
      choices: [
        { value: "health", label: "Health" },
        { value: "unit", label: "Business unit" },
      ],
      defaultValue: "health",
    },
  ],
};

const perWeek: ChartQueryDef = {
  id: "executivesFiledPerWeek",
  category: "Research",
  label: "Executives mapped per week",
  description: "Across every open position — not cumulative",
  shape: "TIME_SERIES",
  notAdditive: false,
  defaultChart: "line",
  charts: ["line", "area", "column", "table"],
  options: [],
};

function result(overrides: Partial<ChartResult>): ChartResult {
  return {
    queryId: "positionsBy",
    shape: "CATEGORICAL",
    allowedCharts: ["bar", "column", "pie", "donut", "table"],
    options: {},
    dimension: "Health",
    measure: "Positions",
    unit: "",
    labels: ["On track", "At risk"],
    values: [5, 3],
    series: [],
    suppressed: [],
    kpi: null,
    columns: [],
    rows: [],
    note: null,
    percent: false,
    truncated: false,
    ...overrides,
  };
}

const pipeline: Dashboard = {
  id: "d1",
  name: "Pipeline across positions",
  shared: true,
  owned: true,
  widgets: [{ queryId: "positionsBy", chart: "donut", options: { group: "health" }, title: null, size: "M" }],
  version: 3,
  updatedAt: "2026-11-01T10:00:00Z",
};

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <MemoryRouter initialEntries={["/dashboards"]}>
          <WorkspaceDashboardsPage />
        </MemoryRouter>
      </ToastProvider>
    </QueryClientProvider>,
  );
}

describe("WorkspaceDashboardsPage", () => {
  beforeEach(() => {
    mode = "COMPANY";
    vi.mocked(dashboardsApi.listDashboards).mockReset().mockResolvedValue([pipeline]);
    vi.mocked(dashboardsApi.getChartLibrary).mockReset().mockResolvedValue([positionsBy, perWeek]);
    const answerFor = (queryId: string) =>
      queryId === "executivesFiledPerWeek"
        ? result({ queryId, shape: "TIME_SERIES", allowedCharts: ["line", "area", "column", "table"],
            dimension: "Week ending", labels: ["7 Sept", "14 Sept"], values: [4, 7] })
        : result({});
    vi.mocked(dashboardsApi.runChartQuery).mockReset().mockImplementation(async (_scope, queryId) => answerFor(queryId));
    vi.mocked(dashboardsApi.getChartAnswers).mockReset().mockImplementation(async (_scope, widgets) =>
      widgets.map((widget) =>
        widget.queryId === "retired"
          ? { queryId: widget.queryId, result: null, code: "CHART_QUERY_NOT_FOUND" }
          : { queryId: widget.queryId, result: answerFor(widget.queryId), code: null },
      ),
    );
    vi.mocked(dashboardsApi.updateDashboard).mockReset();
  });

  it("draws a saved chart live, titled from its query and options", async () => {
    renderPage();
    const chart = await screen.findByRole("region", { name: "Open positions by health" });
    expect(await within(chart).findByText("On track")).toBeInTheDocument();
    expect(within(chart).getByText("63%")).toBeInTheDocument();
    expect(screen.getByText(/Shared with the workspace team/)).toBeInTheDocument();
  });

  it("adds a chart from the library: only the types that fit can be picked, and the layout saves", async () => {
    const user = userEvent.setup();
    vi.mocked(dashboardsApi.updateDashboard).mockImplementation(async (_scope, _id, body) => ({
      ...pipeline,
      widgets: body.widgets,
      version: 4,
    }));
    renderPage();
    await screen.findByRole("region", { name: "Open positions by health" });

    await user.click(screen.getByRole("button", { name: "Edit layout" }));
    await user.click(screen.getAllByRole("button", { name: "Add chart" })[0]);
    const drawer = await screen.findByRole("dialog", { name: "Chart library" });
    await user.click(within(drawer).getByRole("button", { name: /Executives mapped per week/ }));

    const pie = await within(drawer).findByRole("radio", { name: "Pie" });
    expect(pie).toHaveAttribute("aria-disabled", "true");
    await user.click(within(drawer).getByRole("radio", { name: "Area" }));
    expect(within(drawer).getByRole("radio", { name: "Area" })).toHaveAttribute("aria-checked", "true");
    await user.click(within(drawer).getByRole("button", { name: "Add to dashboard" }));
    await user.click(within(drawer).getByRole("button", { name: "Done" }));

    await user.click(screen.getByRole("button", { name: "Save layout" }));
    await waitFor(() => expect(dashboardsApi.updateDashboard).toHaveBeenCalled());
    const [, id, body] = vi.mocked(dashboardsApi.updateDashboard).mock.calls[0];
    expect(id).toBe("d1");
    expect(body.version).toBe(3);
    expect(body.widgets.map((widget) => [widget.queryId, widget.chart])).toEqual([
      ["positionsBy", "donut"],
      ["executivesFiledPerWeek", "area"],
    ]);
  });

  it("names the workspace's units in its own words", async () => {
    mode = "AGENCY";
    const user = userEvent.setup();
    renderPage();
    await screen.findByRole("region", { name: "Open positions by health" });
    await user.click(screen.getByRole("button", { name: "Edit chart" }));
    const drawer = await screen.findByRole("dialog", { name: "Chart library" });
    expect(within(drawer).getByText(/Open positions by health, stage, client or lead/)).toBeInTheDocument();
  });

  it("says so when a chart's query has left the library, rather than drawing it", async () => {
    vi.mocked(dashboardsApi.listDashboards).mockResolvedValue([
      { ...pipeline, widgets: [{ queryId: "retired", chart: "bar", options: {}, title: "Old chart", size: "M" }] },
    ]);
    renderPage();
    expect(await screen.findByText(/This chart is no longer available/)).toBeInTheDocument();
    expect(dashboardsApi.runChartQuery).not.toHaveBeenCalled();
  });

  it("answers every chart of a dashboard in one request", async () => {
    vi.mocked(dashboardsApi.listDashboards).mockResolvedValue([
      { ...pipeline, widgets: [...pipeline.widgets, { queryId: "executivesFiledPerWeek", chart: "line", options: {}, title: null, size: "M" }] },
    ]);
    renderPage();
    await screen.findByRole("region", { name: "Executives mapped per week" });
    await waitFor(() => expect(screen.getByText("On track")).toBeInTheDocument());
    expect(dashboardsApi.getChartAnswers).toHaveBeenCalledTimes(1);
    expect(dashboardsApi.runChartQuery).not.toHaveBeenCalled();
  });

  it("offers a first dashboard when there is none", async () => {
    vi.mocked(dashboardsApi.listDashboards).mockResolvedValue([]);
    renderPage();
    expect(await screen.findByText("No dashboards yet")).toBeInTheDocument();
  });
});
