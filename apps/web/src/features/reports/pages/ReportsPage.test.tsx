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
    expect(within(rail).getByText("Diversity & DEI")).toBeInTheDocument();
    // Findings are computed from the report, not typed: the ceiling's rank and the slip both come
    // out of the disclosures and the cumulative coverage respectively.
    expect(screen.getByText("38th percentile")).toBeInTheDocument();
    expect(screen.getByText("36 days")).toBeInTheDocument();
    expect(screen.getByText("6 nationalities")).toBeInTheDocument();
  });

  it("re-projects when the reader switches to the full-mandate average", async () => {
    vi.mocked(reportApi.getReport).mockResolvedValue(SAMPLE_REPORT);
    const user = userEvent.setup();

    renderPage();
    await screen.findByText("36 days");

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

  it("draws the gender pipeline from the recorded rows, never from the headcount", async () => {
    vi.mocked(reportApi.getReport).mockResolvedValue(SAMPLE_REPORT);

    renderPage();

    // 37 women of the 114 with a gender on file, and the thinnest level named rather than averaged away.
    expect(await screen.findByText("32% of the recorded pool")).toBeInTheDocument();
    expect(screen.getByText("Board (20%)")).toBeInTheDocument();
    expect(screen.getByText(/114 recorded · aggregate only/)).toBeInTheDocument();
  });

  it("says gender is unmeasured rather than drawing a pool of one gender", async () => {
    vi.mocked(reportApi.getReport).mockResolvedValue({
      ...SAMPLE_REPORT,
      diversity: {
        ...SAMPLE_REPORT.diversity,
        genderByLevel: SAMPLE_REPORT.diversity.genderByLevel.map((row) => ({
          ...row,
          female: 0,
          male: 0,
          other: 0,
        })),
        genderUnrecorded: 116,
      },
    });

    renderPage();

    expect(await screen.findByText("Nobody on this mandate has a gender recorded.")).toBeInTheDocument();
    expect(screen.queryByText(/of the recorded pool/)).not.toBeInTheDocument();
  });

  it("marks the relevance mix as illustrative so three tidy bands are not read as a finding", async () => {
    vi.mocked(reportApi.getReport).mockResolvedValue(SAMPLE_REPORT);

    renderPage();

    expect(await screen.findByText("Relevance mix")).toBeInTheDocument();
    expect(screen.getByText(/These bands are not derived from your rows/)).toBeInTheDocument();
  });

  it("names the cross-mandate benchmarks it does not have rather than leaving a silent gap", async () => {
    vi.mocked(reportApi.getReport).mockResolvedValue(SAMPLE_REPORT);

    renderPage();

    expect(await screen.findByText(/Cross-mandate compensation benchmark/)).toBeInTheDocument();
    expect(screen.getByText(/Cross-mandate diversity benchmark/)).toBeInTheDocument();
  });

  it("falls back to the hub bars alone where no map is configured", async () => {
    vi.mocked(reportApi.getReport).mockResolvedValue(SAMPLE_REPORT);

    renderPage();
    await screen.findByText("Where talent sits");

    // The config read is unmocked and fails in jsdom, which is the no-token case: bars, no map.
    expect(screen.getByTitle(/38 executives in Dubai/)).toBeInTheDocument();
    expect(screen.queryByLabelText("Talent hubs on a map")).not.toBeInTheDocument();
  });
});
