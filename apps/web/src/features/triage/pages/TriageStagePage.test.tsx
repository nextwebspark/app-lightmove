import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Outlet, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui/Toast";
import { AuthProvider } from "../../auth/AuthProvider";
import type { Project } from "../../projects/api/types";
import * as triageApi from "../api/triageApi";
import type { TriageCompaniesPage, TriageCompany } from "../api/types";
import { TriageStagePage } from "./TriageStagePage";

vi.mock("../../auth/api/authApi");
vi.mock("../api/triageApi", async (importOriginal) => ({
  // Keys are real; only the calls are mocked.
  ...(await importOriginal<typeof triageApi>()),
  getTriageCompanies: vi.fn(),
  updateTriageCompany: vi.fn(),
  deleteTriageCompany: vi.fn(),
  captureCompany: vi.fn(),
}));
vi.mock("../../../lib/apiClient", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../../lib/apiClient")>()),
  restoreSession: vi.fn(),
  setAccessToken: vi.fn(),
}));

const { restoreSession } = await import("../../../lib/apiClient");

const lead = {
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
    roles: ["MEMBER" as const],
  },
};

/** A portal guest: seated CLIENT on this mandate, so WORK_VIEW and nothing else. */
const representative = {
  ...lead,
  id: "u9",
  fullName: "Dana Client",
  workspace: { ...lead.workspace, roles: ["CLIENT" as const] },
};

const project = {
  id: "p1",
  positionTitle: "CFO",
  team: [
    { memberId: "m1", userId: "u1", fullName: "Alok Kumar", avatarUrl: null,
      workspaceRoles: ["MEMBER"], projectRoles: ["LEAD"] },
    { memberId: "m9", userId: "u9", fullName: "Dana Client", avatarUrl: null,
      workspaceRoles: ["CLIENT"], projectRoles: ["CLIENT"] },
  ],
} as unknown as Project;

const acwa: TriageCompany = {
  id: "u1",
  apolloAccountId: "a1",
  source: "strategy",
  status: "inUniverse",
  note: null,
  companyName: "ACWA Power",
  industry: "oil & energy",
  companyCountry: "Saudi Arabia",
  companyCity: "Riyadh",
  numEmployees: 3000,
  annualRevenue: null,
  website: null,
  companyLinkedinUrl: null,
  foundedYear: null,
  shortDescription: null,
  sourceUrl: null,
  logoUrl: null,
  addedAt: "2026-08-01T09:00:00Z",
};

const pageOf = (overrides: Partial<TriageCompaniesPage> = {}): TriageCompaniesPage => ({
  companies: [acwa],
  totalCount: 1,
  page: 0,
  size: 25,
  counts: { inUniverse: 1, shortlisted: 0, declined: 0 },
  ...overrides,
});

/** The page reads the project from ProjectLayout's outlet — a bare shell stands in for the layout. */
const renderStage = (slug = "universe") =>
  render(
    <MemoryRouter initialEntries={[`/projects/p1/companies/${slug}`]}>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <AuthProvider>
          <ToastProvider>
            <Routes>
              <Route element={<Outlet context={{ project }} />}>
                <Route path="/projects/:projectId/companies/:stage" element={<TriageStagePage />} />
              </Route>
            </Routes>
          </ToastProvider>
        </AuthProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );

/**
 * The Companies section: three stages of a mandate's triaged universe, each its own page, rendered in
 * the same grid Strategy uses — plus the three writes the section added (move, remove, capture).
 */
describe("TriageStagePage", () => {
  beforeEach(async () => {
    // resetAllMocks strips implementations set in the mock factory, so they are set here or not at all.
    vi.resetAllMocks();
    vi.mocked(restoreSession).mockResolvedValue("token");
    const authApi = await import("../../auth/api/authApi");
    vi.mocked(authApi.me).mockResolvedValue(lead);
    vi.mocked(triageApi.getTriageCompanies).mockResolvedValue(pageOf());
  });

  it("reads the stage from the URL and asks the API for that status", async () => {
    vi.mocked(triageApi.getTriageCompanies).mockResolvedValue(
      pageOf({ companies: [{ ...acwa, status: "shortlisted" }], counts: { inUniverse: 0, shortlisted: 1, declined: 0 } }),
    );
    renderStage("shortlisted");

    expect(await screen.findByText("ACWA Power")).toBeInTheDocument();
    expect(vi.mocked(triageApi.getTriageCompanies).mock.calls[0][1]).toBe("shortlisted");
  });

  it("renders the companies in the shared grid, with their source", async () => {
    renderStage();

    // The same role structure Strategy's table produces — that is the point of the shared DataGrid.
    const grid = await screen.findByRole("table", { name: /In universe companies/i });
    expect(within(grid).getByText("ACWA Power")).toBeInTheDocument();
    expect(within(grid).getByText("Saudi Arabia")).toBeInTheDocument();
    expect(within(grid).getByText("Strategy")).toBeInTheDocument();
  });

  it("shows the three stage counts, so a move is visibly reflected", async () => {
    vi.mocked(triageApi.getTriageCompanies).mockResolvedValue(
      pageOf({ counts: { inUniverse: 4, shortlisted: 2, declined: 7 } }),
    );
    renderStage();

    expect(await screen.findByRole("link", { name: /In universe/ })).toHaveTextContent("4");
    expect(screen.getByRole("link", { name: /Shortlisted/ })).toHaveTextContent("2");
    expect(screen.getByRole("link", { name: /Declined/ })).toHaveTextContent("7");
  });

  it("moves a company through the existing PATCH rather than a stage-specific endpoint", async () => {
    vi.mocked(triageApi.updateTriageCompany).mockResolvedValue({ ...acwa, status: "shortlisted" });
    renderStage();

    await screen.findByText("ACWA Power");
    await userEvent.click(screen.getByRole("button", { name: /Shortlist: ACWA Power/i }));

    await waitFor(() =>
      expect(triageApi.updateTriageCompany).toHaveBeenCalledWith("p1", "u1", { status: "shortlisted" }),
    );
  });

  it("only offers the moves a company has not already made", async () => {
    vi.mocked(triageApi.getTriageCompanies).mockResolvedValue(
      pageOf({ companies: [{ ...acwa, status: "declined" }] }),
    );
    renderStage("declined");

    await screen.findByText("ACWA Power");
    // Every button on a row should do something: a declined company is not offered Decline.
    expect(screen.queryByRole("button", { name: /^Decline: ACWA Power/i })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Back to universe: ACWA Power/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Shortlist: ACWA Power/i })).toBeInTheDocument();
  });

  it("confirms a removal, and says the company itself is not deleted", async () => {
    vi.mocked(triageApi.deleteTriageCompany).mockResolvedValue(undefined);
    renderStage();

    await screen.findByText("ACWA Power");
    await userEvent.click(screen.getByRole("button", { name: /Remove ACWA Power from this mandate/i }));

    // The distinction the dialog exists to make: this drops a decision, not a company. Deleting from
    // the Apollo universe is not something the product can do, and must not be what the copy implies.
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText(/company itself is not deleted/i)).toBeInTheDocument();
    expect(triageApi.deleteTriageCompany).not.toHaveBeenCalled();

    await userEvent.click(within(dialog).getByRole("button", { name: /Remove from mandate/i }));
    await waitFor(() => expect(triageApi.deleteTriageCompany).toHaveBeenCalledWith("p1", "u1"));
  });

  it("captures a hand-typed company into the stage being viewed", async () => {
    vi.mocked(triageApi.captureCompany).mockResolvedValue({
      ...acwa, id: "u2", apolloAccountId: null, source: "manual", status: "shortlisted",
      companyName: "Gulf Industrial",
    });
    renderStage("shortlisted");

    await screen.findByRole("table", { name: /Shortlisted companies/i });
    await userEvent.click(screen.getByRole("button", { name: /Add company/i }));

    const dialog = await screen.findByRole("dialog", { name: /Add a company/i });
    await userEvent.type(within(dialog).getByLabelText(/Company name/i), "Gulf Industrial");
    await userEvent.type(within(dialog).getByLabelText(/^Employees$/i), "2400");
    await userEvent.click(within(dialog).getByRole("button", { name: /^Add company$/i }));

    await waitFor(() =>
      expect(triageApi.captureCompany).toHaveBeenCalledWith("p1", expect.objectContaining({
        companyName: "Gulf Industrial",
        source: "manual",
        // Added while looking at the shortlist means shortlisted — not bounced to the universe.
        status: "shortlisted",
        numEmployees: 2400,
      })),
    );
  });

  it("leaves a blank number out rather than sending it as zero", async () => {
    vi.mocked(triageApi.captureCompany).mockResolvedValue({ ...acwa, source: "manual" });
    renderStage();

    await screen.findByRole("table", { name: /In universe companies/i });
    await userEvent.click(screen.getByRole("button", { name: /Add company/i }));

    const dialog = await screen.findByRole("dialog", { name: /Add a company/i });
    await userEvent.type(within(dialog).getByLabelText(/Company name/i), "Quiet Holdings");
    await userEvent.click(within(dialog).getByRole("button", { name: /^Add company$/i }));

    // "No published headcount" and "a headcount of zero" are different claims about a company.
    await waitFor(() => expect(triageApi.captureCompany).toHaveBeenCalled());
    const payload = vi.mocked(triageApi.captureCompany).mock.calls[0][1];
    expect(payload.numEmployees).toBeUndefined();
    expect(payload.annualRevenue).toBeUndefined();
  });

  it("shows a refused read as a failure, not as an empty stage", async () => {
    vi.mocked(triageApi.getTriageCompanies).mockRejectedValue(new Error("403"));
    renderStage();

    // A 403 rendered as "nothing here" states as fact a number the caller was not allowed to read.
    expect(await screen.findByText(/could not be loaded/i)).toBeInTheDocument();
    expect(screen.queryByText(/No companies in the universe yet/i)).not.toBeInTheDocument();
  });

  it("points an empty universe at Strategy rather than showing the market", async () => {
    vi.mocked(triageApi.getTriageCompanies).mockResolvedValue(
      pageOf({ companies: [], totalCount: 0, counts: { inUniverse: 0, shortlisted: 0, declined: 0 } }),
    );
    renderStage();

    expect(await screen.findByText(/No companies in the universe yet/i)).toBeInTheDocument();
  });

  it("gives a client representative the grid and none of the writes", async () => {
    const authApi = await import("../../auth/api/authApi");
    vi.mocked(authApi.me).mockResolvedValue(representative);
    renderStage("universe");

    expect(await screen.findByText("ACWA Power")).toBeInTheDocument();
    // Mirrors the server: reading is WORK_VIEW, every write is WORK_EXECUTE. Offering the buttons
    // would only ever earn a 403.
    await waitFor(() =>
      expect(screen.queryByRole("button", { name: /Shortlist: ACWA Power/i })).not.toBeInTheDocument(),
    );
    expect(screen.queryByRole("button", { name: /Add company/i })).not.toBeInTheDocument();
  });

  it("redirects an unknown stage instead of rendering an empty grid for it", async () => {
    renderStage("nonsense");

    expect(await screen.findByRole("table", { name: /In universe companies/i })).toBeInTheDocument();
  });
});
