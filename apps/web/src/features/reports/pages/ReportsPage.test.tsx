import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Outlet, Route, Routes, useLocation } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Project } from "../../projects/api/types";
import * as reportApi from "../api/reportApi";
import { SAMPLE_REPORT } from "../../../test/sampleReport";
import { ReportsPage } from "./ReportsPage";

vi.mock("../api/reportApi", async (importOriginal) => ({
  // The query key is real; only the call is mocked.
  ...(await importOriginal<typeof import("../api/reportApi")>()),
  getReport: vi.fn(),
}));

/**
 * The Reports tab: one chapter at a time behind a chapter menu, the figures that move when a reader
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

  /** A tile's share of the treemap, in percent — what its area says about its sector. */
  const areaOf = (tile: HTMLElement) =>
    (parseFloat(tile.style.width) * parseFloat(tile.style.height)) / 100;

  function LocationProbe() {
    return <output aria-label="location">{useLocation().search}</output>;
  }

  // The page reads the project from ProjectLayout's outlet — a bare shell stands in for the layout.
  const renderPage = (chapter?: string, client = new QueryClient({ defaultOptions: { queries: { retry: false } } })) =>
    render(
      <MemoryRouter initialEntries={[`/projects/p1/reports${chapter ? `?chapter=${chapter}` : ""}`]}>
        <QueryClientProvider client={client}>
          <Routes>
            <Route element={<Outlet context={{ project }} />}>
              <Route
                path="/projects/:projectId/reports"
                element={
                  <>
                    <ReportsPage />
                    <LocationProbe />
                  </>
                }
              />
            </Route>
          </Routes>
        </QueryClientProvider>
      </MemoryRouter>,
    );

  beforeEach(() => {
    vi.resetAllMocks();
  });

  it("states a refused read instead of reporting a map of zeros", async () => {
    vi.mocked(reportApi.getReport).mockRejectedValue(new Error("forbidden"));

    renderPage();

    expect(await screen.findByText("Couldn't load this report")).toBeInTheDocument();
    expect(screen.queryByText("Companies mapped")).not.toBeInTheDocument();
    expect(screen.queryByText("Mapping progress")).not.toBeInTheDocument();
  });

  it("re-reads the report every time the tab is opened, so an edit made on another tab is counted", async () => {
    vi.mocked(reportApi.getReport).mockResolvedValue(SAMPLE_REPORT);
    // The app's own default: without an override the second visit is answered from the cache.
    const client = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: 30_000 } } });

    const firstVisit = renderPage(undefined, client);
    await screen.findByText("36 days");
    firstVisit.unmount();
    renderPage(undefined, client);

    await waitFor(() => expect(reportApi.getReport).toHaveBeenCalledTimes(2));
  });

  it("opens on mapping progress, beside a menu of the four chapters", async () => {
    vi.mocked(reportApi.getReport).mockResolvedValue(SAMPLE_REPORT);

    renderPage();

    expect(await screen.findByRole("heading", { level: 1, name: "Are we going to hit the deadline?" })).toBeInTheDocument();
    const rail = screen.getByRole("navigation", { name: "Report chapters" });
    expect(within(rail).getByRole("link", { name: /Mapping progress/ })).toHaveAttribute("aria-current", "page");
    expect(within(rail).getByRole("link", { name: /Shape of the market/ })).toBeInTheDocument();
    expect(within(rail).getByRole("link", { name: /Remuneration/ })).toBeInTheDocument();
    expect(within(rail).getByRole("link", { name: /Diversity & DEI/ })).toBeInTheDocument();
    // The finding is computed from the cumulative coverage, not typed.
    expect(screen.getByText("36 days")).toBeInTheDocument();
    // One chapter at a time: the others' figures are not on the page.
    expect(screen.queryByText("38th percentile")).not.toBeInTheDocument();
  });

  it("moves to the chapter a reader picks and keeps it in the URL", async () => {
    vi.mocked(reportApi.getReport).mockResolvedValue(SAMPLE_REPORT);
    const user = userEvent.setup();

    renderPage();
    await screen.findByText("36 days");

    await user.click(screen.getByRole("link", { name: /Remuneration/ }));

    expect(screen.getByRole("heading", { level: 1, name: /Are we underpaying/ })).toBeInTheDocument();
    expect(screen.getByText("38th percentile")).toBeInTheDocument();
    expect(screen.queryByText("36 days")).not.toBeInTheDocument();
    expect(screen.getByLabelText("location")).toHaveTextContent("?chapter=comp");
  });

  it("opens the chapter the URL names, and mapping progress for one it does not have", async () => {
    vi.mocked(reportApi.getReport).mockResolvedValue(SAMPLE_REPORT);

    const { unmount } = renderPage("dei");
    expect(await screen.findByText("6 nationalities")).toBeInTheDocument();
    unmount();

    renderPage("appendix");
    expect(await screen.findByRole("heading", { level: 1, name: "Are we going to hit the deadline?" })).toBeInTheDocument();
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

    renderPage("market");
    await screen.findByText("Sector × seniority");

    await user.click(screen.getByRole("button", { name: "FMCG · C-Suite: 16 executives" }));

    const drawer = screen.getByRole("dialog", { name: "FMCG · C-Suite" });
    expect(within(drawer).getByText("Sara Fadel")).toBeInTheDocument();
    expect(within(drawer).getByText("Almarai")).toBeInTheDocument();
    // Sixteen in the cell, six listed: the roster and the bar over it are said to be a sample.
    expect(within(drawer).getByText("Executives in this slice · first 6 of 16")).toBeInTheDocument();

    await user.keyboard("{Escape}");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("says so when a filter leaves too few disclosures for a percentile", async () => {
    vi.mocked(reportApi.getReport).mockResolvedValue(SAMPLE_REPORT);
    const user = userEvent.setup();

    renderPage("comp");
    await screen.findByText("38th percentile");

    await user.selectOptions(screen.getByRole("combobox", { name: "Country" }), "Kuwait");

    expect(screen.getByText(/too few to compute a reliable percentile/)).toBeInTheDocument();
  });

  it("says the brief has no band rather than ranking against nothing", async () => {
    vi.mocked(reportApi.getReport).mockResolvedValue({
      ...SAMPLE_REPORT,
      remuneration: { ...SAMPLE_REPORT.remuneration, fixedBand: null, packageBand: null },
    });

    renderPage("comp");

    expect(await screen.findByText("no salary band")).toBeInTheDocument();
    expect(screen.queryByText("38th percentile")).not.toBeInTheDocument();
  });

  it("answers a nationality requirement with the executives who actually qualify", async () => {
    vi.mocked(reportApi.getReport).mockResolvedValue(SAMPLE_REPORT);
    const user = userEvent.setup();

    renderPage("dei");
    await screen.findByText("6 nationalities");

    await user.selectOptions(screen.getByRole("combobox", { name: "Nationality requirement" }), "GCC nationals");
    await user.selectOptions(screen.getByRole("combobox", { name: "Seniority level" }), "C-Suite");

    expect(screen.getByText(/of the 51 executives mapped in that scope/)).toBeInTheDocument();
    expect(screen.getByText("a GCC national")).toBeInTheDocument();

    // An expat group is not a nationality, so the requirement is not worded as one.
    await user.selectOptions(screen.getByRole("combobox", { name: "Nationality requirement" }), "Arab expat, non-GCC");
    expect(screen.getByText("an Arab expat, non-GCC executive")).toBeInTheDocument();
  });

  it("draws the gender pipeline from the recorded rows, never from the headcount", async () => {
    vi.mocked(reportApi.getReport).mockResolvedValue(SAMPLE_REPORT);

    renderPage("dei");

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

    renderPage("dei");

    expect(await screen.findByText("Nobody on this mandate has a gender recorded.")).toBeInTheDocument();
    expect(screen.queryByText(/of the recorded pool/)).not.toBeInTheDocument();
  });

  it("names the cross-mandate benchmarks it does not have rather than leaving a silent gap", async () => {
    vi.mocked(reportApi.getReport).mockResolvedValue(SAMPLE_REPORT);

    const { unmount } = renderPage("comp");
    expect(await screen.findByText(/Cross-mandate compensation benchmark/)).toBeInTheDocument();
    unmount();

    renderPage("dei");
    expect(await screen.findByText(/Cross-mandate diversity benchmark/)).toBeInTheDocument();
  });

  it("draws the sector universe as a treemap, each tile sized by its share", async () => {
    vi.mocked(reportApi.getReport).mockResolvedValue(SAMPLE_REPORT);

    renderPage("market");
    await screen.findByText("Companies by sector");

    expect(screen.getByText("n=42")).toBeInTheDocument();
    // Area, not length, is what a treemap states: 14 of the 42 sectored companies is a third of it.
    const leader = screen.getByTitle("FMCG — 14 companies, 33.3% of the sectored universe");
    expect(areaOf(leader)).toBeCloseTo(33.3, 1);
    expect(within(leader).getByText("33.3%")).toBeInTheDocument();
    expect(areaOf(screen.getByTitle("Other — 2 companies, 4.8% of the sectored universe"))).toBeCloseTo(4.8, 1);
  });

  it("heads a chapter with its question alone, not with a screen counter", async () => {
    vi.mocked(reportApi.getReport).mockResolvedValue(SAMPLE_REPORT);

    renderPage();
    await screen.findByText("36 days");

    expect(screen.queryByText(/^Screen \d/)).not.toBeInTheDocument();
  });

  it("falls back to the country bars alone where no map is configured", async () => {
    vi.mocked(reportApi.getReport).mockResolvedValue(SAMPLE_REPORT);

    renderPage("market");
    await screen.findByText("Where talent sits");

    // The config read is unmocked and fails in jsdom, which is the no-token case: bars, no map.
    expect(screen.getByTitle(/54 executives in United Arab Emirates/)).toBeInTheDocument();
    expect(screen.queryByLabelText("Talent by country on a map")).not.toBeInTheDocument();
  });
});
