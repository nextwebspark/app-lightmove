import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Outlet, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Project } from "../../projects/api/types";
import * as reportApi from "../api/reportApi";
import { SAMPLE_REPORT } from "../mock/sampleReport";
import { ReportsPage } from "./ReportsPage";

vi.mock("../api/reportApi", async (importOriginal) => ({
  // The query key is real; only the call is mocked.
  ...(await importOriginal<typeof import("../api/reportApi")>()),
  getReport: vi.fn(),
}));

/**
 * The Reports tab: the four chapters drawn from one report, the figures that move when a reader
 * changes the basis or the filter, the drill-in drawers, and — the one that matters — a refused read
 * never rendering as a report full of zeros.
 */
describe("ReportsPage", () => {
  const project: Project = {
    id: "p1",
    clientId: "c1",
    clientName: "Meridian Foods",
    clientLogoUrl: null,
    positionTitle: "Chief Financial Officer",
    stage: "MAPPING",
    health: "OK",
    targetDate: "2026-09-01",
    team: [],
    representatives: [],
    companies: 0,
    candidates: 0,
    createdAt: "2026-07-21T10:00:00Z",
  };

  // The page reads the project from ProjectLayout's outlet — a bare shell stands in for the layout.
  const renderPage = () =>
    render(
      <MemoryRouter initialEntries={["/projects/p1/reports"]}>
        <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
          <Routes>
            <Route element={<Outlet context={{ project }} />}>
              <Route path="/projects/:projectId/reports" element={<ReportsPage />} />
            </Route>
          </Routes>
        </QueryClientProvider>
      </MemoryRouter>,
    );

  beforeEach(() => {
    vi.resetAllMocks();
    // The rail's scroll-spy needs one; jsdom has no IntersectionObserver.
    vi.stubGlobal(
      "IntersectionObserver",
      class {
        observe() {}
        disconnect() {}
      },
    );
  });

  it("states a refused read instead of reporting a map of zeros", async () => {
    vi.mocked(reportApi.getReport).mockRejectedValue(new Error("forbidden"));

    renderPage();

    expect(await screen.findByText("Couldn't load this report")).toBeInTheDocument();
    expect(screen.queryByText("Companies mapped")).not.toBeInTheDocument();
    expect(screen.queryByText("Mapping progress")).not.toBeInTheDocument();
  });

  it("heads the report with the mandate and walks the four chapters", async () => {
    vi.mocked(reportApi.getReport).mockResolvedValue(SAMPLE_REPORT);

    renderPage();

    expect(await screen.findByRole("heading", { level: 1, name: "Chief Financial Officer — Meridian Foods" })).toBeInTheDocument();
    const rail = screen.getByRole("navigation", { name: "On this page" });
    expect(within(rail).getByText("Mapping progress")).toBeInTheDocument();
    expect(within(rail).getByText("Shape of the market")).toBeInTheDocument();
    expect(within(rail).getByText("Remuneration")).toBeInTheDocument();
    expect(within(rail).getByText("Nationality & localisation")).toBeInTheDocument();
    // Findings are computed from the report, not typed: the ceiling's rank and the slip both come
    // out of the disclosures and the cumulative coverage respectively.
    expect(screen.getAllByText("38th percentile").length).toBeGreaterThan(0);
    expect(screen.getAllByText("36 days").length).toBeGreaterThan(0);
    expect(screen.getByText("6 nationalities")).toBeInTheDocument();
  });

  it("re-projects when the reader switches to the full-mandate average", async () => {
    vi.mocked(reportApi.getReport).mockResolvedValue(SAMPLE_REPORT);
    const user = userEvent.setup();

    renderPage();
    await screen.findAllByText("36 days");

    await user.click(screen.getByRole("radio", { name: "Full-mandate avg" }));

    expect(screen.getByText("24 days")).toBeInTheDocument();
  });

  it("opens a heat-matrix cell as a market slice with its executives", async () => {
    vi.mocked(reportApi.getReport).mockResolvedValue(SAMPLE_REPORT);
    const user = userEvent.setup();

    renderPage();
    await screen.findAllByText("38th percentile");

    await user.click(screen.getByRole("button", { name: "FMCG · C-Suite: 16 executives" }));

    const drawer = screen.getByRole("dialog", { name: "FMCG · C-Suite" });
    expect(within(drawer).getByText("Sara Fadel")).toBeInTheDocument();
    expect(within(drawer).getByText("Almarai")).toBeInTheDocument();

    await user.keyboard("{Escape}");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("says so when a filter leaves too few disclosures for a percentile", async () => {
    vi.mocked(reportApi.getReport).mockResolvedValue(SAMPLE_REPORT);
    const user = userEvent.setup();

    renderPage();
    await screen.findAllByText("38th percentile");

    await user.selectOptions(screen.getByRole("combobox", { name: "Country" }), "Kuwait");

    expect(screen.getByText(/too few to compute a reliable percentile/)).toBeInTheDocument();
  });

  it("says the brief has no band rather than ranking against nothing", async () => {
    vi.mocked(reportApi.getReport).mockResolvedValue({
      ...SAMPLE_REPORT,
      remuneration: { ...SAMPLE_REPORT.remuneration, fixedBand: null, packageBand: null },
    });

    renderPage();

    expect(await screen.findByText("no salary band")).toBeInTheDocument();
    expect(screen.queryByText("38th percentile")).not.toBeInTheDocument();
  });

  it("answers a nationality requirement with the executives who actually qualify", async () => {
    vi.mocked(reportApi.getReport).mockResolvedValue(SAMPLE_REPORT);
    const user = userEvent.setup();

    renderPage();
    await screen.findByText("6 nationalities");

    await user.selectOptions(screen.getByRole("combobox", { name: "Nationality requirement" }), "GCC nationals");
    await user.selectOptions(screen.getByRole("combobox", { name: "Seniority level" }), "C-Suite");

    expect(screen.getByText(/of the 51 executives mapped in that scope/)).toBeInTheDocument();
    expect(screen.getByText("a GCC national")).toBeInTheDocument();
  });
});
