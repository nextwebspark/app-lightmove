import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Outlet, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui/Toast";
import { AuthProvider } from "../../auth/AuthProvider";
import * as candidatesApi from "../../candidates/api/candidatesApi";
import type { Candidate, CandidatesPage } from "../../candidates/api/types";
import type { Project } from "../../projects/api/types";
import * as companiesApi from "../../strategy/api/companiesApi";
import type { CompanyResult, Facets } from "../../strategy/api/types";
import * as triageApi from "../api/triageApi";
import type { TriageCompaniesPage, TriageCompany } from "../api/types";
import { TriageStagePage } from "./TriageStagePage";

vi.mock("../../auth/api/authApi");
vi.mock("../../candidates/api/candidatesApi", async (importOriginal) => ({
  // Keys are real; only the calls are mocked.
  ...(await importOriginal<typeof candidatesApi>()),
  getCandidates: vi.fn(),
  createCandidate: vi.fn(),
  updateCandidate: vi.fn(),
  deleteCandidate: vi.fn(),
}));
vi.mock("../api/triageApi", async (importOriginal) => ({
  // Keys are real; only the calls are mocked.
  ...(await importOriginal<typeof triageApi>()),
  getTriageCompanies: vi.fn(),
  updateTriageCompany: vi.fn(),
  deleteTriageCompany: vi.fn(),
  captureCompany: vi.fn(),
  addMarketCompany: vi.fn(),
  editTriageCompany: vi.fn(),
}));
vi.mock("../../strategy/api/companiesApi", async (importOriginal) => ({
  // The Add form reads the market: its picker searches the universe and its Sector and Country
  // fields offer the same vocabulary the Strategy filter is expressed in.
  ...(await importOriginal<typeof companiesApi>()),
  searchCompanies: vi.fn(),
  getCompany: vi.fn(),
  getFacets: vi.fn(),
}));
vi.mock("../../../lib/apiClient", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../../lib/apiClient")>()),
  restoreSession: vi.fn(),
  setAccessToken: vi.fn(),
  // The page holds a live stream open; here it simply never speaks, so the grid behaves exactly as
  // it does between events and the fetch-mocked queries stay the only data source.
  streamEvents: vi.fn(() => new Promise<void>(() => {})),
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

const yasmin: Candidate = {
  id: "c1",
  triageCompanyId: "u1",
  companyName: "ACWA Power",
  fullName: "Yasmin El-Sayed",
  title: "VP Finance",
  seniority: "N-1",
  status: "engaged",
  email: null,
  phone: null,
  linkedinUrl: null,
  locationCountry: null,
  locationCity: null,
  nationality: null,
  yearsExperience: null,
  summary: null,
  note: null,
  compensation: {
    currency: null, baseSalary: null, bonus: null, allowances: null,
    longTermIncentive: null, noticePeriod: null,
  },
  career: [],
  languages: [],
  source: "manual",
  sourceUrl: null,
  addedAt: "2026-08-02T09:00:00Z",
  enrichedAt: null,
};

/** Two of each, so a filtered list can be shown to have left something out. */
const FACETS: Facets = {
  sectorGroups: [
    {
      name: "Energy & Resources",
      industries: [{ value: "oil & energy", label: "Oil & Energy", count: 240 }],
    },
    {
      name: "Industrials",
      industries: [{ value: "manufacturing", label: "Manufacturing", count: 610 }],
    },
  ],
  adjacentIndustries: {},
  marketSegments: [],
  employeeBands: [],
  revenueBands: [],
};

/** The market's own record of a company, as the picker reads it back once one is chosen. */
const marketAcwa = {
  apolloAccountId: "a7",
  companyName: "ACWA Power",
  industry: "oil & energy",
  companyCountry: "Saudi Arabia",
  companyCity: "Riyadh",
  numEmployees: 3000,
  annualRevenue: 6_000_000_000,
  website: "https://acwapower.example",
  logoUrl: null,
  shortDescription: "Develops and operates power and desalination plants.",
  foundedYear: 2004,
  companyLinkedinUrl: null,
  keywords: [],
  technologies: [],
  sicCodes: [],
  naicsCodes: [],
} as unknown as CompanyResult;

const peopleOf = (candidates: Candidate[]): CandidatesPage => ({
  candidates,
  totalCount: candidates.length,
  page: 0,
  size: 25,
});

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
    vi.mocked(candidatesApi.getCandidates).mockResolvedValue(peopleOf([]));
    vi.mocked(companiesApi.getFacets).mockResolvedValue(FACETS);
    vi.mocked(companiesApi.searchCompanies).mockResolvedValue({ companies: [] });
    vi.mocked(companiesApi.getCompany).mockResolvedValue(marketAcwa);
  });

  it("reads the stage from the URL and asks the API for that status", async () => {
    vi.mocked(triageApi.getTriageCompanies).mockResolvedValue(
      pageOf({ companies: [{ ...acwa, status: "shortlisted" }], counts: { inUniverse: 0, shortlisted: 1, declined: 0 } }),
    );
    renderStage("shortlisted");

    expect(await screen.findByText("ACWA Power")).toBeInTheDocument();
    expect(vi.mocked(triageApi.getTriageCompanies).mock.calls[0][1]).toBe("shortlisted");
  });

  it("renders the companies in the shared grid", async () => {
    renderStage();

    // The same role structure Strategy's table produces — that is the point of the shared DataGrid.
    const grid = await screen.findByRole("table", { name: /In universe companies/i });
    expect(within(grid).getByText("ACWA Power")).toBeInTheDocument();
    expect(within(grid).getByText("Saudi Arabia")).toBeInTheDocument();
    // Source is off by default: it is provenance for a reader questioning a figure, not a column to
    // carry on every scan. The Columns picker still has it, and the panel always shows it.
    expect(within(grid).queryByText("Strategy")).not.toBeInTheDocument();
    expect(within(grid).queryByRole("columnheader", { name: /Source/i })).not.toBeInTheDocument();
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

  it("takes a company the market already carries by its universe id", async () => {
    vi.mocked(companiesApi.searchCompanies).mockResolvedValue({
      companies: [
        { apolloAccountId: "a7", companyName: "ACWA Power", industry: "oil & energy",
          companyCity: "Riyadh", companyCountry: "Saudi Arabia", website: null, logoUrl: null,
          numEmployees: 3000 },
      ],
    });
    vi.mocked(triageApi.addMarketCompany).mockResolvedValue({ ...acwa, status: "shortlisted" });
    renderStage("shortlisted");

    await screen.findByRole("table", { name: /Shortlisted companies/i });
    await userEvent.click(screen.getByRole("button", { name: /Add company/i }));

    const dialog = await screen.findByRole("dialog", { name: /Add a company/i });
    await userEvent.type(within(dialog).getByLabelText(/Company name/i), "acwa");
    await userEvent.click(await within(dialog).findByRole("button", { name: /ACWA Power/i }));

    // The record is shown before it is taken — and shown, not offered for editing: these fields
    // belong to the export, and the server resolves them from the universe whatever this screen has.
    expect(await within(dialog).findByText("$6B")).toBeInTheDocument();
    expect(within(dialog).getByText("3,000")).toBeInTheDocument();
    expect(within(dialog).getByText("2004")).toBeInTheDocument();
    expect(within(dialog).getByText(/desalination plants/i)).toBeInTheDocument();
    expect(within(dialog).queryByLabelText(/^Employees$/i)).not.toBeInTheDocument();
    expect(within(dialog).queryByLabelText(/^Sector$/i)).not.toBeInTheDocument();

    await userEvent.type(within(dialog).getByLabelText(/^Note/i), "Met their CFO");
    await userEvent.click(within(dialog).getByRole("button", { name: /^Add company$/i }));

    // A company the market holds is taken by id and never re-typed: the server resolves the snapshot,
    // so the row keeps the export's figures and a Source badge that means what it says.
    await waitFor(() =>
      expect(triageApi.addMarketCompany).toHaveBeenCalledWith("p1", "a7", {
        status: "shortlisted",
        note: "Met their CFO",
      }),
    );
    expect(triageApi.captureCompany).not.toHaveBeenCalled();
  });

  it("keeps the by-hand door open when the market cannot be searched", async () => {
    vi.mocked(companiesApi.searchCompanies).mockRejectedValue(new Error("500"));
    vi.mocked(triageApi.captureCompany).mockResolvedValue({ ...acwa, source: "manual" });
    renderStage();

    await screen.findByRole("table", { name: /In universe companies/i });
    await userEvent.click(screen.getByRole("button", { name: /Add company/i }));

    const dialog = await screen.findByRole("dialog", { name: /Add a company/i });
    await userEvent.type(within(dialog).getByLabelText(/Company name/i), "A Quiet Family Holding");

    // A company the export does not carry is the case this door exists for, so a universe that
    // cannot be reached must not be what closes it. The label says what is uncertain instead.
    const byHand = await within(dialog).findByRole("button", { name: /could not be searched/i });
    expect(within(dialog).getByText(/Try again in a moment/i)).toBeInTheDocument();

    await userEvent.click(byHand);
    expect(within(dialog).getByLabelText(/Company name/i)).toHaveValue("A Quiet Family Holding");
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
    // The market answers nothing, so the escape hatch is the only way on — and it carries the name
    // that was already typed rather than asking for it twice.
    await userEvent.click(await within(dialog).findByRole("button", { name: /as a new company/i }));

    expect(within(dialog).getByLabelText(/Company name/i)).toHaveValue("Gulf Industrial");
    await userEvent.type(within(dialog).getByLabelText(/^Employees$/i), "2400");

    // Typed, then picked — the taxonomy runs to 148 industries, so the box is searched rather than
    // scrolled, the way the Strategy filter offers the same values.
    await userEvent.type(within(dialog).getByLabelText(/^Sector$/i), "oil");
    expect(within(dialog).queryByRole("option", { name: /Manufacturing/i })).not.toBeInTheDocument();
    await userEvent.click(await within(dialog).findByRole("option", { name: /Oil & Energy/i }));

    await userEvent.type(within(dialog).getByLabelText(/^Country$/i), "saudi");
    await userEvent.click(await within(dialog).findByRole("option", { name: /Saudi Arabia/i }));

    await userEvent.click(within(dialog).getByRole("button", { name: /^Add company$/i }));

    await waitFor(() =>
      expect(triageApi.captureCompany).toHaveBeenCalledWith("p1", expect.objectContaining({
        companyName: "Gulf Industrial",
        source: "manual",
        // Added while looking at the shortlist means shortlisted — not bounced to the universe.
        status: "shortlisted",
        numEmployees: 2400,
        // Picked from the market's own vocabulary, so a hand-typed company files under the names the
        // Strategy filter searches by rather than one reader's wording of them.
        industry: "oil & energy",
        companyCountry: "Saudi Arabia",
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
    await userEvent.click(await within(dialog).findByRole("button", { name: /as a new company/i }));
    await userEvent.click(within(dialog).getByRole("button", { name: /^Add company$/i }));

    // "No published headcount" and "a headcount of zero" are different claims about a company.
    await waitFor(() => expect(triageApi.captureCompany).toHaveBeenCalled());
    const payload = vi.mocked(triageApi.captureCompany).mock.calls[0][1];
    expect(payload.numEmployees).toBeUndefined();
    expect(payload.annualRevenue).toBeUndefined();
  });

  it("keeps a sector the taxonomy does not carry rather than clearing it on save", async () => {
    const captured: TriageCompany = {
      ...acwa, source: "extension", apolloAccountId: null, industry: "widget assembly",
    };
    vi.mocked(triageApi.getTriageCompanies).mockResolvedValue(pageOf({ companies: [captured] }));
    vi.mocked(triageApi.editTriageCompany).mockResolvedValue(captured);
    renderStage();

    await userEvent.click(await screen.findByRole("button", { name: /Open ACWA Power/i }));
    const dialog = await screen.findByRole("dialog", { name: /ACWA Power/i });
    await userEvent.click(within(dialog).getByRole("button", { name: /^Edit$/i }));

    // The plugin reads whatever a page publishes, so a captured sector need not be one of Apollo's.
    // A select that silently dropped it would clear a field the consultant never touched.
    expect(within(dialog).getByLabelText(/^Sector$/i)).toHaveValue("widget assembly");
    await userEvent.click(within(dialog).getByRole("button", { name: /Save changes/i }));

    await waitFor(() =>
      expect(triageApi.editTriageCompany).toHaveBeenCalledWith("p1", "u1", expect.objectContaining({
        industry: "widget assembly",
      })),
    );
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

  it("offers the empty slot on a company with nobody mapped", async () => {
    renderStage();

    await screen.findByText("ACWA Power");
    // A company nobody has looked at yet is the most useful thing this grid shows, so its executive
    // cell is the invitation rather than a dash.
    expect(screen.getByRole("button", { name: /\+ Add executive/i })).toBeInTheDocument();
  });

  it("gives every executive at a company its own row, with the company repeated", async () => {
    // Scope-aware: the page runs two reads — the people at this page's companies, and the ones
    // mapped to no company at all — and answering both with the same list would not be a mapping.
    vi.mocked(candidatesApi.getCandidates).mockImplementation(async (_project, scope) =>
      peopleOf(
        scope.unmapped ? [] : [yasmin, { ...yasmin, id: "c2", fullName: "Omar Haddad", title: "CFO" }],
      ),
    );
    renderStage();

    expect(await screen.findByText("Yasmin El-Sayed")).toBeInTheDocument();
    expect(screen.getByText("Omar Haddad")).toBeInTheDocument();
    // Two people at one company is two lines, and the company is on both of them.
    expect(screen.getAllByText("ACWA Power")).toHaveLength(2);
    // With somebody mapped there is no empty slot left on that company.
    expect(screen.queryByRole("button", { name: /\+ Add executive/i })).not.toBeInTheDocument();
  });

  it("asks only for the people at the companies on this page", async () => {
    renderStage();

    await screen.findByText("ACWA Power");
    await waitFor(() =>
      expect(candidatesApi.getCandidates).toHaveBeenCalledWith(
        "p1",
        { triageCompanyIds: ["u1"] },
        expect.anything(),
      ),
    );
  });

  it("maps a new executive at the company whose row was used", async () => {
    vi.mocked(candidatesApi.createCandidate).mockResolvedValue({ ...yasmin, id: "c3" });
    renderStage();

    await screen.findByText("ACWA Power");
    await userEvent.click(screen.getByRole("button", { name: /\+ Add executive/i }));

    const drawer = await screen.findByRole("dialog", { name: /Add executive/i });
    // The employer comes from the row, not from typing: the mapping and the name must not disagree.
    expect(within(drawer).getByLabelText(/^Employer$/i)).toHaveValue("ACWA Power");

    await userEvent.type(within(drawer).getByLabelText(/Full name/i), "Yasmin El-Sayed");
    await userEvent.click(within(drawer).getByRole("button", { name: /^Add executive$/i }));

    await waitFor(() =>
      expect(candidatesApi.createCandidate).toHaveBeenCalledWith(
        "p1",
        expect.objectContaining({ fullName: "Yasmin El-Sayed", triageCompanyId: "u1" }),
      ),
    );
  });

  it("opens an existing executive as a profile, and edits behind a second step", async () => {
    vi.mocked(candidatesApi.getCandidates).mockImplementation(async (_project, scope) =>
      peopleOf(scope.unmapped ? [] : [yasmin]),
    );
    renderStage();

    await userEvent.click(await screen.findByRole("button", { name: /Yasmin El-Sayed/i }));

    const drawer = await screen.findByRole("dialog", { name: /Yasmin El-Sayed/i });
    expect(within(drawer).getByRole("heading", { name: "Yasmin El-Sayed" })).toBeInTheDocument();
    expect(within(drawer).queryByLabelText(/Full name/i)).not.toBeInTheDocument();

    await userEvent.click(within(drawer).getByRole("button", { name: /^Edit$/i }));
    expect(within(drawer).getByLabelText(/Full name/i)).toHaveValue("Yasmin El-Sayed");
    expect(within(drawer).getByLabelText(/^Title$/i)).toHaveValue("VP Finance");
  });

  it("shows executives whose employer is not in the universe after the companies", async () => {
    const unmapped = {
      ...yasmin, id: "c9", triageCompanyId: null, companyName: "An Unlisted Holding",
      fullName: "Wei Ling Tan",
    };
    vi.mocked(candidatesApi.getCandidates).mockImplementation(async (_project, scope) =>
      peopleOf(scope.unmapped ? [unmapped] : []),
    );
    renderStage();

    expect(await screen.findByText("Wei Ling Tan")).toBeInTheDocument();
    // The row says where they work and that it is not a company this screen can act on.
    expect(screen.getByText("An Unlisted Holding")).toBeInTheDocument();
    expect(screen.getByText(/Not in universe/i)).toBeInTheDocument();
  });

  it("says so when the server could not fit every executive on the page", async () => {
    vi.mocked(candidatesApi.getCandidates).mockImplementation(async (_project, scope) =>
      scope.unmapped
        ? peopleOf([])
        : { ...peopleOf([yasmin]), totalCount: 137 },
    );
    renderStage();

    // A mapping that ran past the server's cap would otherwise render fewer lines with nothing saying
    // so — a talent map that looks complete and is not.
    expect(
      await screen.findByText(/Showing 1 of 137 executives at these companies/i),
    ).toBeInTheDocument();
  });

  it("says nothing when everything fitted", async () => {
    vi.mocked(candidatesApi.getCandidates).mockImplementation(async (_project, scope) =>
      peopleOf(scope.unmapped ? [] : [yasmin]),
    );
    renderStage();

    await screen.findByText("Yasmin El-Sayed");
    expect(screen.queryByText(/Showing .* of .* executives/i)).not.toBeInTheDocument();
  });

  it("names no page size of its own — the server sizes the people read", async () => {
    renderStage();

    await screen.findByText("ACWA Power");
    // A client that computes its own size has to know the server's ceiling to stay under it, and the
    // first attempt at that landed exactly on it.
    await waitFor(() =>
      expect(candidatesApi.getCandidates).toHaveBeenCalledWith(
        "p1",
        { triageCompanyIds: ["u1"] },
        expect.anything(),
      ),
    );
  });

  it("opens a company as a read-only panel from its name", async () => {
    renderStage();

    await userEvent.click(await screen.findByRole("button", { name: /Open ACWA Power/i }));

    const panel = await screen.findByRole("dialog", { name: /ACWA Power/i });
    expect(within(panel).getByRole("heading", { name: "ACWA Power" })).toBeInTheDocument();
    // The header's meta line: sector, city and country, as the mockup's panel carries them.
    expect(within(panel).getByText(/oil & energy · Riyadh · Saudi Arabia/)).toBeInTheDocument();
    expect(within(panel).queryByLabelText(/Company name/i)).not.toBeInTheDocument();
  });

  it("offers no Edit on a company taken from the market, but still takes a note", async () => {
    vi.mocked(triageApi.updateTriageCompany).mockResolvedValue(acwa);
    renderStage();

    await userEvent.click(await screen.findByRole("button", { name: /Open ACWA Power/i }));
    const panel = await screen.findByRole("dialog", { name: /ACWA Power/i });

    // Its fields are the export's snapshot, refreshed by the export — rewriting them would make the
    // Source badge a claim the figures no longer support.
    expect(within(panel).queryByRole("button", { name: /^Edit$/i })).not.toBeInTheDocument();
    expect(within(panel).getByText(/come from the market export/i)).toBeInTheDocument();

    // The note is the mandate's own remark, so it is editable on every company including this one.
    await userEvent.type(within(panel).getByLabelText(/Note on this company/i), "Adjacent");
    await userEvent.click(within(panel).getByRole("button", { name: /^Save$/i }));

    await waitFor(() =>
      expect(triageApi.updateTriageCompany).toHaveBeenCalledWith("p1", "u1", { note: "Adjacent" }),
    );
  });

  it("edits a hand-typed company behind the Edit button", async () => {
    const gulf = { ...acwa, id: "u2", apolloAccountId: null, source: "manual" as const,
      companyName: "Gulf Industrial" };
    vi.mocked(triageApi.getTriageCompanies).mockResolvedValue(pageOf({ companies: [gulf] }));
    vi.mocked(triageApi.editTriageCompany).mockResolvedValue({ ...gulf, numEmployees: 2400 });
    renderStage();

    await userEvent.click(await screen.findByRole("button", { name: /Open Gulf Industrial/i }));
    const panel = await screen.findByRole("dialog", { name: /Gulf Industrial/i });
    await userEvent.click(within(panel).getByRole("button", { name: /^Edit$/i }));

    await userEvent.clear(within(panel).getByLabelText(/^Employees$/i));
    await userEvent.type(within(panel).getByLabelText(/^Employees$/i), "2400");
    await userEvent.click(within(panel).getByRole("button", { name: /Save changes/i }));

    await waitFor(() =>
      expect(triageApi.editTriageCompany).toHaveBeenCalledWith(
        "p1",
        "u2",
        expect.objectContaining({ companyName: "Gulf Industrial", numEmployees: 2400 }),
      ),
    );
  });

  it("gives a client representative the company panel and none of its controls", async () => {
    const authApi = await import("../../auth/api/authApi");
    vi.mocked(authApi.me).mockResolvedValue(representative);
    const gulf = { ...acwa, apolloAccountId: null, source: "manual" as const };
    vi.mocked(triageApi.getTriageCompanies).mockResolvedValue(pageOf({ companies: [gulf] }));
    renderStage();

    await userEvent.click(await screen.findByRole("button", { name: /Open ACWA Power/i }));
    const panel = await screen.findByRole("dialog", { name: /ACWA Power/i });

    // Hand-typed, so a colleague would get Edit here — WORK_VIEW does not.
    expect(within(panel).queryByRole("button", { name: /^Edit$/i })).not.toBeInTheDocument();
    expect(within(panel).queryByRole("button", { name: /^Save$/i })).not.toBeInTheDocument();
    expect(within(panel).queryByRole("button", { name: /^Remove$/i })).not.toBeInTheDocument();
  });

  it("gives a client representative the executive columns and none of the writes", async () => {
    const authApi = await import("../../auth/api/authApi");
    vi.mocked(authApi.me).mockResolvedValue(representative);
    vi.mocked(candidatesApi.getCandidates).mockImplementation(async (_project, scope) =>
      peopleOf(scope.unmapped ? [] : [yasmin]),
    );
    renderStage();

    // WORK_VIEW covers the people as well as the companies — a mandate a client can follow.
    expect(await screen.findByText("Yasmin El-Sayed")).toBeInTheDocument();
    expect(screen.getByText("Engaged")).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.queryByRole("button", { name: /Add an executive at ACWA Power/i })).not.toBeInTheDocument(),
    );
    expect(screen.queryByRole("button", { name: /^Add executive$/i })).not.toBeInTheDocument();
  });
});
