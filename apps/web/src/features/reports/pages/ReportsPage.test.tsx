import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { MemoryRouter, Outlet, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AuthProvider } from "../../auth/AuthProvider";
import * as authApi from "../../auth/api/authApi";
import type { WorkspaceRole } from "../../auth/api/types";
import type { Project } from "../../projects/api/types";
import * as reportApi from "../api/reportApi";
import type { Report } from "../api/types";
import { ReportsPage } from "./ReportsPage";

vi.mock("../../auth/api/authApi");
vi.mock("../api/reportApi", async (importOriginal) => ({
  // The query key is real; only the call is mocked.
  ...(await importOriginal<typeof import("../api/reportApi")>()),
  getReport: vi.fn(),
}));
vi.mock("../../../lib/apiClient", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../../lib/apiClient")>()),
  restoreSession: vi.fn(),
  setAccessToken: vi.fn(),
}));

const { restoreSession } = await import("../../../lib/apiClient");

/**
 * The Reports tab: the scope measured against the company universe, the sections that have nothing
 * behind them saying so, and — the one that matters — a refused read never rendering as a universe
 * of zero.
 */
describe("ReportsPage", () => {
  const project: Project = {
    id: "p1",
    clientId: "c1",
    clientName: "Aurora Capital",
    positionTitle: "Chief Financial Officer",
    stage: "MAPPING",
    health: "OK",
    targetDate: null,
    team: [],
    representatives: [],
    companies: 0,
    candidates: 0,
    createdAt: "2026-07-13T10:00:00Z",
  };

  const report: Report = {
    universeCount: 42,
    offLimitsCompanies: 1,
    sectorsInScope: 2,
    marketsInScope: 4,
    sectors: [
      { label: "Retail", count: 26 },
      { label: "Grocery Stores", count: 16 },
    ],
    countries: [{ label: "AE", count: 30 }],
    cities: [{ label: "Dubai", count: 22 }],
    mandateBand: null,
    caveats: { revenueBandExcludesUnknown: false },
  };

  const userWith = (roles: WorkspaceRole[]) => ({
    id: "u1",
    email: "alok@firm.example",
    fullName: "Alok Kumar",
    title: null,
    avatarUrl: null,
    emailVerified: true,
    hasPassword: true,
    timezone: "Asia/Dubai",
    locale: "en",
    pendingInvitation: null,
    workspace: {
      id: "w1",
      name: "Firm",
      slug: "firm",
      logoMark: "F",
      emailDomain: "firm.example",
      joinedAt: null,
      roles,
    },
  });

  // The page reads the project from ProjectLayout's outlet — a bare shell stands in for the layout.
  const renderPage = () =>
    render(
      <MemoryRouter initialEntries={["/projects/p1/reports"]}>
        <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
          <AuthProvider>
            <Routes>
              <Route element={<Outlet context={{ project }} />}>
                <Route path="/projects/:projectId/reports" element={<ReportsPage />} />
              </Route>
            </Routes>
          </AuthProvider>
        </QueryClientProvider>
      </MemoryRouter>,
    );

  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(restoreSession).mockResolvedValue("token");
    vi.mocked(authApi.me).mockResolvedValue(userWith(["ADMIN"]));
    // The nav's scroll-spy needs one; jsdom has no IntersectionObserver.
    vi.stubGlobal(
      "IntersectionObserver",
      class {
        observe() {}
        disconnect() {}
      },
    );
  });

  it("states a refused read instead of reporting a universe of zero", async () => {
    vi.mocked(reportApi.getReport).mockRejectedValue(new Error("forbidden"));

    renderPage();

    expect(await screen.findByText("Couldn't load this report")).toBeInTheDocument();
    // Every figure on this page is a stated measurement, so none of them may render off a refusal.
    expect(screen.queryByText("Companies in scope")).not.toBeInTheDocument();
    expect(screen.queryByText(/companies in scope/i)).not.toBeInTheDocument();
  });

  it("measures the saved scope and names the sections nothing backs yet", async () => {
    vi.mocked(reportApi.getReport).mockResolvedValue(report);

    renderPage();

    expect(await screen.findByText("Companies in scope")).toBeInTheDocument();
    expect(screen.getByText("42")).toBeInTheDocument();
    // Twice on purpose: the leading sector heads section 02, and it also labels its own bar.
    expect(screen.getAllByText("Retail")).toHaveLength(2);
    expect(screen.getByText("Dubai")).toBeInTheDocument();
    // The five executive-derived sections say so rather than showing a zero as a finding.
    expect(screen.getAllByText("Not measured yet").length).toBeGreaterThanOrEqual(5);
  });

  it("asks for the compensation band while the brief carries none", async () => {
    vi.mocked(reportApi.getReport).mockResolvedValue(report);

    renderPage();

    expect(await screen.findByText("State the compensation band")).toBeInTheDocument();
    expect(screen.getByText("The position brief states no compensation band.")).toBeInTheDocument();
  });

  it("says what the figures do not cover, and stays silent when they cover everything", async () => {
    vi.mocked(reportApi.getReport).mockResolvedValue({
      ...report,
      caveats: {
        revenueBandExcludesUnknown: true,
      },
    });

    const { unmount } = renderPage();

    expect(await screen.findByText("What these figures do not cover")).toBeInTheDocument();
    // One caveat is left. The other two — an unenforceable off-limits bar and a sector the source
    // did not carry — were artefacts of the report and triage reading different universes.
    expect(screen.getByText(/no revenue figure are excluded/)).toBeInTheDocument();

    unmount();
    vi.mocked(reportApi.getReport).mockResolvedValue(report);
    renderPage();

    // A permanent disclaimer stops being read, so a clean scope shows none.
    expect(await screen.findByText("Companies in scope")).toBeInTheDocument();
    expect(screen.queryByText("What these figures do not cover")).not.toBeInTheDocument();
  });

  it("keeps the firm's own worklist away from a pure client, nav entry included", async () => {
    vi.mocked(authApi.me).mockResolvedValue(userWith(["CLIENT"]));
    vi.mocked(reportApi.getReport).mockResolvedValue(report);

    renderPage();

    // The report itself is written for them — they still get the figures.
    expect(await screen.findByText("Companies in scope")).toBeInTheDocument();
    // Next actions is not: every item tells them to go edit a screen they can only read.
    expect(screen.queryByText("Next actions")).not.toBeInTheDocument();
    expect(screen.queryByText("State the compensation band")).not.toBeInTheDocument();
  });
});
