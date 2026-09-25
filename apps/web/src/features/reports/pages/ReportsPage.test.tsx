import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Outlet, Route, Routes, useLocation } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import * as candidatesApi from "../../candidates/api/candidatesApi";
import type { Candidate } from "../../candidates/api/types";
import * as customColumnsApi from "../../customcolumns/api/customColumnsApi";
import type { Project } from "../../projects/api/types";
import * as reportApi from "../api/reportApi";
import { SAMPLE_REPORT } from "../../../test/sampleReport";
import { SAMPLE_TEAM_PERFORMANCE } from "../../../test/sampleTeamPerformance";
import { ReportsPage } from "./ReportsPage";

vi.mock("../api/reportApi", async (importOriginal) => ({
  // The query key is real; only the call is mocked.
  ...(await importOriginal<typeof import("../api/reportApi")>()),
  getReport: vi.fn(),
  getTeamPerformance: vi.fn(),
}));

vi.mock("../../candidates/api/candidatesApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../candidates/api/candidatesApi")>()),
  getCandidate: vi.fn(),
}));

vi.mock("../../customcolumns/api/customColumnsApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../customcolumns/api/customColumnsApi")>()),
  getCustomColumns: vi.fn(),
}));

// No seat and no admin role by default: the chapters every reader sees, the staff-only card absent.
let viewerRoles: string[] = ["MEMBER"];
vi.mock("../../auth/AuthProvider", () => ({
  useAuth: () => ({ user: { id: "u-viewer", workspace: { roles: viewerRoles } } }),
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
    projectType: "SEARCH",
    startDate: null,
    deliveryDate: null,
    mappingTargetDate: null,
    team: [],
    representatives: [],
    companies: 0,
    candidates: 0,
    mappedCandidates: 0,
    engagedCandidates: 0,
    mappedCompanies: 0,
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
    viewerRoles = ["MEMBER"];
  });

  it("shows researcher performance to staff and never asks for it on a client's behalf", async () => {
    vi.mocked(reportApi.getReport).mockResolvedValue(SAMPLE_REPORT);
    vi.mocked(reportApi.getTeamPerformance).mockResolvedValue(SAMPLE_TEAM_PERFORMANCE);

    const asClient = renderPage();
    await screen.findByText("Recent momentum");
    expect(screen.queryByText("Researcher performance")).not.toBeInTheDocument();
    expect(reportApi.getTeamPerformance).not.toHaveBeenCalled();
    asClient.unmount();

    viewerRoles = ["ADMIN"];
    renderPage();
    expect(await screen.findByText("Researcher performance")).toBeInTheDocument();
    expect(await screen.findByRole("button", { name: /Omar Khoury/ })).toBeInTheDocument();
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

  /** A mandate days old, which is where the chart has a point and no line to draw through it. */
  const firstWeek = (over: Partial<typeof SAMPLE_REPORT.progress>) => ({
    ...SAMPLE_REPORT,
    progress: {
      ...SAMPLE_REPORT.progress,
      kickoff: "2026-09-17",
      asOf: "2026-09-18",
      targetDate: "2026-09-26",
      targetCompanies: 12,
      companiesCumulative: [1],
      weekly: [{ weekEnding: "2026-09-23", identified: 1 }],
      daily: [0, 1],
      ...over,
    },
  });

  it("says a first week has no line rather than drawing a projection onto today", async () => {
    vi.mocked(reportApi.getReport).mockResolvedValue(firstWeek({}));

    renderPage();

    expect(await screen.findByText(/Only the kickoff week has closed/)).toBeInTheDocument();
    expect(screen.getByText("Too early to project")).toBeInTheDocument();
    expect(screen.getByText(/A pace needs a second week/)).toBeInTheDocument();
    // Nothing to project from, so neither the basis control nor a projected date is offered.
    expect(screen.queryByRole("radio", { name: "Full-mandate avg" })).not.toBeInTheDocument();
    expect(screen.queryByText(/projected 17 Sept/)).not.toBeInTheDocument();
    expect(screen.queryByText("Projected")).not.toBeInTheDocument();
  });

  it("reads a universe covered inside the kickoff week as complete, not as a projection", async () => {
    vi.mocked(reportApi.getReport).mockResolvedValue(firstWeek({ targetCompanies: 2, companiesCumulative: [2] }));

    renderPage();

    expect(await screen.findByText(/already has an executive mapped, inside the kickoff week/)).toBeInTheDocument();
    expect(screen.queryByText("Projected")).not.toBeInTheDocument();
  });

  it("drops the projection once every company is covered, and keeps the line", async () => {
    vi.mocked(reportApi.getReport).mockResolvedValue({
      ...SAMPLE_REPORT,
      progress: { ...SAMPLE_REPORT.progress, companiesCumulative: [0, 20, 42] },
    });

    renderPage();

    expect(await screen.findByText(/there is nothing left to project/)).toBeInTheDocument();
    expect(screen.getByText("Actual")).toBeInTheDocument();
    expect(screen.queryByText("Projected")).not.toBeInTheDocument();
  });

  it("names a stalled mandate as unprojectable instead of projecting from a zero pace", async () => {
    vi.mocked(reportApi.getReport).mockResolvedValue({
      ...SAMPLE_REPORT,
      progress: { ...SAMPLE_REPORT.progress, companiesCumulative: [0, 10, 10, 10, 10] },
    });

    renderPage();

    expect(await screen.findByText(/no new company at all/)).toBeInTheDocument();
    expect(screen.queryByText("Projected")).not.toBeInTheDocument();
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

  it("opens a compensation dot as the executive's full profile, read by their id", async () => {
    vi.mocked(reportApi.getReport).mockResolvedValue(SAMPLE_REPORT);
    vi.mocked(customColumnsApi.getCustomColumns).mockResolvedValue({ columns: [] });
    const zahrani: Candidate = {
      id: "d1",
      triageCompanyId: null,
      companyName: "Al Ain Farms",
      fullName: "H. Al-Zahrani",
      title: "VP Finance",
      seniority: null,
      status: "interested",
      linkedinUrl: null,
      locationCountry: "United Arab Emirates",
      locationCity: "Abu Dhabi",
      nationality: "Saudi",
      gender: null,
      yearsExperience: null,
      aiInferredFields: [],
      summary: null,
      note: null,
      compensation: {
        currency: "AED",
        baseSalary: null,
        bonus: null,
        allowances: null,
        longTermIncentive: null,
        noticePeriod: null,
        allowanceLines: [],
        longTermIncentiveTypes: [],
      },
      career: [{ company: "Regional Foods Co.", title: "Finance Director", period: "2017–2021" }],
      languages: [],
      education: [],
      skills: [],
      source: "manual",
      sourceUrl: null,
      customFields: {},
      addedAt: "2026-08-02T09:00:00Z",
      enrichedAt: null,
      contacts: { emails: [], phones: [], emailsLookedUpAt: null, phonesLookedUpAt: null, source: null },
    };
    vi.mocked(candidatesApi.getCandidate).mockResolvedValue(zahrani);
    const user = userEvent.setup();

    renderPage("comp");
    await user.click(await screen.findByRole("button", { name: /^H\. Al-Zahrani ·/ }));

    const drawer = await screen.findByRole("dialog", { name: "H. Al-Zahrani" });
    expect(candidatesApi.getCandidate).toHaveBeenCalledWith("p1", "d1", expect.anything());
    expect(within(drawer).getByText("Regional Foods Co.")).toBeInTheDocument();
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

  it("states a missing nationality the way it states a missing gender, rather than drawing a blank ring", async () => {
    vi.mocked(reportApi.getReport).mockResolvedValue({
      ...SAMPLE_REPORT,
      diversity: { ...SAMPLE_REPORT.diversity, nationalities: [], gccNationals: 0, unknownNationality: 116 },
    });

    renderPage("dei");

    // Both the checker and the mix say it, in the gender card's words.
    expect(await screen.findAllByText("Nobody on this mandate has a nationality recorded.")).toHaveLength(2);
    // And nothing claims to have measured it: no 0-of-0 scope, no 0% GCC share, no filters to set.
    expect(screen.queryByText(/of the 0 executives mapped in that scope/)).not.toBeInTheDocument();
    expect(screen.queryByRole("combobox", { name: "Nationality requirement" })).not.toBeInTheDocument();
    expect(screen.getByText("no nationality recorded yet")).toBeInTheDocument();
  });

  it("names the cross-mandate benchmarks it does not have rather than leaving a silent gap", async () => {
    vi.mocked(reportApi.getReport).mockResolvedValue(SAMPLE_REPORT);

    const { unmount } = renderPage("comp");
    expect(await screen.findByText(/Cross-mandate compensation benchmark/)).toBeInTheDocument();
    unmount();

    renderPage("dei");
    expect(await screen.findByText(/Cross-mandate diversity benchmark/)).toBeInTheDocument();
  });

  it("draws only the pockets a sparse mandate has reached, not a field of hatching", async () => {
    vi.mocked(reportApi.getReport).mockResolvedValue({
      ...SAMPLE_REPORT,
      market: {
        ...SAMPLE_REPORT.market,
        cells: SAMPLE_REPORT.market.cells.map((cell) =>
          cell.sector === "FMCG" && cell.level === "C-Suite" ? { ...cell, count: 1 } : { ...cell, count: 0 },
        ),
      },
    });

    renderPage("market");
    await screen.findByText("Sector × seniority");

    // One executive at one pocket: one row, one column, and no Board or N-2 row of hatching.
    const matrix = screen.getByRole("button", { name: "FMCG · C-Suite: 1 executives" });
    expect(matrix).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Board: 0 executives/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /F&B/ })).not.toBeInTheDocument();
  });

  it("draws the sector universe as a treemap, each tile sized by its share", async () => {
    vi.mocked(reportApi.getReport).mockResolvedValue(SAMPLE_REPORT);

    renderPage("market");
    await screen.findByText("Companies by sector");

    expect(screen.getByText("n=42")).toBeInTheDocument();
    // Area, not length, is what a treemap states: 14 of the 42 sectored companies is a third of it.
    const leader = screen.getByRole("img", { name: "FMCG — 14 companies, 33.3% of the sectored universe" });
    expect(areaOf(leader)).toBeCloseTo(33.3, 1);
    expect(within(leader).getByText("33.3%")).toBeInTheDocument();
    expect(areaOf(screen.getByRole("img", { name: "Other — 2 companies, 4.8% of the sectored universe" }))).toBeCloseTo(4.8, 1);
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
