import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Outlet, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui/Toast";
import { ApiRequestError } from "../../../lib/apiClient";
import type { Project } from "../../projects/api/types";
import * as companiesApi from "../api/companiesApi";
import * as strategyApi from "../api/strategyApi";
import type { CompanyPage, Facets, SavedSearch, Strategy, StrategyFilter } from "../api/types";
import * as triageApi from "../../triage/api/triageApi";
import { StrategyPage } from "./StrategyPage";

vi.mock("../api/strategyApi", async (importOriginal) => ({
  ...(await importOriginal<typeof strategyApi>()),
  getStrategy: vi.fn(),
  putFilter: vi.fn(),
  getCompanies: vi.fn(),
  saveSearch: vi.fn(),
  patchSearch: vi.fn(),
  overwriteSearch: vi.fn(),
  deleteSearch: vi.fn(),
  putOffLimits: vi.fn(),
}));
// The toolbar splits saved searches into the viewer's own and the mandate's, so the page needs a
// signed-in user. Mocking the hook keeps that to one line instead of standing up a whole session.
vi.mock("../../auth/AuthProvider", () => ({
  useAuth: () => ({ user: { id: "u1", fullName: "Nadia Haddad" } }),
}));
vi.mock("../../triage/api/triageApi", async (importOriginal) => ({
  ...(await importOriginal<typeof triageApi>()),
  addMarketCompany: vi.fn(),
  addAllInScope: vi.fn(),
}));
vi.mock("../api/companiesApi", async (importOriginal) => ({
  ...(await importOriginal<typeof companiesApi>()),
  getFacets: vi.fn(),
  searchCompanies: vi.fn(),
  searchKeywords: vi.fn(),
}));

const project = { id: "p1", positionTitle: "CFO" } as Project;

const EMPTY_FILTER: StrategyFilter = {
  industries: [],
  keywords: [],
  marketSegments: [],
  countries: [],
  employeeBands: [],
  revenueBands: [],
  employeeRange: null,
  revenueRange: null,
};

const FACETS: Facets = {
  sectorGroups: [
    {
      name: "Energy & Utilities",
      industries: [
        { value: "oil & energy", label: "oil & energy", count: 2 },
        { value: "utilities", label: "utilities", count: 1 },
      ],
    },
    {
      name: "Construction",
      industries: [{ value: "construction", label: "construction", count: 5 }],
    },
  ],
  adjacentIndustries: {
    "oil & energy": ["utilities", "construction"],
    utilities: ["oil & energy"],
    construction: ["oil & energy"],
  },
  marketSegments: [{ value: "B2B", label: "B2B", count: 40 }],
  employeeBands: [
    { value: "1001-2000", label: "1001-2000", count: 2022 },
    { value: "2001-5000", label: "2001-5000", count: 640 },
  ],
  revenueBands: [
    { value: "1b-5b", label: "$1B - $5B", count: 289 },
    { value: "unknown", label: "Unknown", count: 64690 },
  ],
};

const savedSearchOf = (overrides: Partial<SavedSearch> = {}): SavedSearch => ({
  id: "s1",
  name: "GCC energy",
  filter: EMPTY_FILTER,
  visibility: "SHARED",
  createdById: "u1",
  createdByName: "Nadia Haddad",
  createdAt: "2026-08-20T09:00:00Z",
  updatedAt: "2026-08-20T09:00:00Z",
  ...overrides,
});

const strategyOf = (filter: StrategyFilter = EMPTY_FILTER, searches: SavedSearch[] = []): Strategy => ({
  filter,
  offLimits: [],
  searches,
});

const pageOf = (overrides: Partial<CompanyPage> = {}): CompanyPage => ({
  companies: [
    {
      apolloAccountId: "a1",
      companyName: "ACWA Power",
      industry: "oil & energy",
      companyCountry: "Saudi Arabia",
      companyCity: "Riyadh",
      numEmployees: 3000,
      annualRevenue: 6_000_000_000,
      website: "https://acwapower.com",
      logoUrl: null,
      shortDescription: "IPP leader",
      foundedYear: 2004,
      companyLinkedinUrl: "https://linkedin.com/company/acwapower",
      facebookUrl: null,
      twitterUrl: null,
      companyPhone: null,
      companyState: null,
      companyAddress: null,
      parentCompany: null,
      totalFunding: null,
      latestFunding: null,
      latestFundingAmount: null,
      lastRaisedAt: null,
      numberOfRetailLocations: null,
      keywords: [],
      technologies: [],
      sicCodes: [],
      naicsCodes: [],
    },
  ],
  totalCount: 1,
  page: 0,
  size: 25,
  ...overrides,
});

const renderPage = (client = new QueryClient({ defaultOptions: { queries: { retry: false } } })) =>
  render(
    <MemoryRouter>
      <QueryClientProvider client={client}>
        <ToastProvider>
          <Routes>
            <Route element={<Outlet context={{ project }} />}>
              <Route path="/" element={<StrategyPage />} />
            </Route>
          </Routes>
        </ToastProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );

describe("StrategyPage — the filter sidebar and its results", () => {
  beforeEach(() => {
    // restoreAllMocks in the shared setup restores spies but leaves a vi.fn()'s call history alone,
    // so without this an assertion on `mock.calls.at(-1)` reads the previous test's last call.
    vi.clearAllMocks();
    // Column visibility is persisted per project, so one test's ticked column is the next one's
    // starting state unless the store is cleared between them.
    localStorage.clear();
    vi.mocked(strategyApi.getStrategy).mockResolvedValue(strategyOf());
    vi.mocked(companiesApi.getFacets).mockResolvedValue(FACETS);
    vi.mocked(companiesApi.searchCompanies).mockResolvedValue({ companies: [] });
    vi.mocked(companiesApi.searchKeywords).mockImplementation(async (query) => ({
      keywords: [{ value: "saas", label: "saas", count: 12 }].filter((keyword) =>
        keyword.value.includes(query),
      ),
    }));
    vi.mocked(strategyApi.getCompanies).mockResolvedValue(pageOf());
    vi.mocked(strategyApi.putFilter).mockImplementation(async (_id, filter) => strategyOf(filter));
  });

  it("opens on the whole universe rather than on nothing", async () => {
    renderPage();

    // The criteria model this replaced refused to answer without a sector. A search screen that
    // opened on zero results would read as an empty market rather than an untouched filter.
    expect(await screen.findByText("ACWA Power")).toBeInTheDocument();
    expect(screen.getByText("1 - 1 of 1")).toBeInTheDocument();
  });

  it("says the counts were refused rather than pulsing at a client representative forever", async () => {
    // A project CLIENT seat holds WORK_VIEW, so the mandate and its results load, but /companies/facets
    // is gated PROJECT_BROWSE and 403s. Rendering the loading skeleton for that left the rail pulsing
    // beside a table that had loaded fine, with nothing on screen saying why.
    vi.mocked(companiesApi.getFacets).mockRejectedValue(new Error("Forbidden"));
    renderPage();

    expect(await screen.findByText("ACWA Power")).toBeInTheDocument();
    const filters = await screen.findByRole("region", { name: "Filters" });
    await userEvent.click(within(filters).getByRole("button", { name: "# Employees" }));
    await waitFor(() =>
      expect(within(filters).getByText(/counts are not available to you/i)).toBeInTheDocument(),
    );
  });

  it("offers Location without the facets read, so a refused count cannot take the axis with it", async () => {
    // The vocabulary is the six GCC markets and the chips carry no count, so nothing about this
    // panel waits on /companies/facets — which is also why it survives that read being refused.
    vi.mocked(companiesApi.getFacets).mockRejectedValue(new Error("Forbidden"));
    renderPage();

    const filters = await screen.findByRole("region", { name: "Filters" });
    for (const country of ["United Arab Emirates", "Saudi Arabia", "Qatar", "Kuwait", "Oman", "Bahrain"]) {
      expect(within(filters).getByRole("button", { name: country })).toBeInTheDocument();
    }
  });

  it("autosaves a chip as a whole-filter snapshot", async () => {
    renderPage();

    await userEvent.click(await screen.findByRole("button", { name: /Qatar/ }));

    await waitFor(() => expect(strategyApi.putFilter).toHaveBeenCalled(), { timeout: 2000 });
    expect(vi.mocked(strategyApi.putFilter).mock.calls[0]![1]).toEqual({
      ...EMPTY_FILTER,
      countries: ["Qatar"],
    });
  });

  it("cancels the results before invalidating them, so a read of the pre-edit scope cannot win", async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const cancel = vi.spyOn(client, "cancelQueries");
    const invalidate = vi.spyOn(client, "invalidateQueries");
    renderPage(client);

    await userEvent.click(await screen.findByRole("button", { name: /Qatar/ }));

    await waitFor(
      () => expect(cancel).toHaveBeenCalledWith({ queryKey: ["strategyCompanies", "p1"] }),
      { timeout: 2000 },
    );
    // A read left running would resolve after the invalidation and reinstate the pre-edit companies
    // as fresh for the whole staleTime.
    expect(cancel.mock.invocationCallOrder[0]).toBeLessThan(invalidate.mock.invocationCallOrder[0]!);
  });

  it("offers the whole industry list on a click, and narrows it as the consultant types", async () => {
    renderPage();
    const filters = await screen.findByRole("region", { name: "Filters" });
    await userEvent.click(within(filters).getByRole("button", { name: /^Industry/ }));

    // Closed, the box says nothing about what can be asked for.
    expect(within(filters).queryAllByRole("option")).toHaveLength(0);

    await userEvent.click(within(filters).getByLabelText("Search industries"));
    // Biggest slice first, and never the sector it is filed under: a group is not selectable.
    expect(within(filters).getAllByRole("option").map((row) => row.textContent)).toEqual([
      "construction5",
      "oil & energy2",
      "utilities1",
    ]);

    await userEvent.type(within(filters).getByLabelText("Search industries"), "oil");
    expect(within(filters).getAllByRole("option")).toHaveLength(1);

    await userEvent.click(within(filters).getByRole("option", { name: /oil & energy/ }));

    // No Apply step: the click is the decision, and the filter's own autosave coalesces a burst.
    await waitFor(
      () =>
        expect(vi.mocked(strategyApi.putFilter).mock.calls.at(-1)![1].industries).toEqual([
          "oil & energy",
        ]),
      { timeout: 2000 },
    );
    expect(within(filters).getByLabelText("Remove oil & energy")).toBeInTheDocument();
  });

  it("does not offer an industry already taken", async () => {
    renderPage();
    const filters = await screen.findByRole("region", { name: "Filters" });
    await userEvent.click(within(filters).getByRole("button", { name: /^Industry/ }));
    await userEvent.type(within(filters).getByLabelText("Search industries"), "oil");
    await userEvent.click(within(filters).getByRole("option", { name: /oil & energy/ }));

    await userEvent.type(within(filters).getByLabelText("Search industries"), "oil");

    // Offering it again would let one industry be added twice and read as two decisions.
    expect(within(filters).queryByRole("option", { name: /oil & energy/ })).not.toBeInTheDocument();
    expect(within(filters).getByText("No industry matches that.")).toBeInTheDocument();
  });

  it("keeps an industry the taxonomy no longer groups", async () => {
    // The grouping is editorial and gets re-tuned, so a saved search can hold a label that no
    // current group claims. Rebuilding the filter from the groups alone would delete it.
    vi.mocked(strategyApi.getStrategy).mockResolvedValue(
      strategyOf({ ...EMPTY_FILTER, industries: ["nanotechnology"] }),
    );
    renderPage();
    const filters = await screen.findByRole("region", { name: "Filters" });
    // A pre-loaded selection puts the clear badge in the header, so its name is no longer bare.
    await userEvent.click(within(filters).getByRole("button", { name: /^Industry/ }));
    expect(within(filters).getByLabelText("Remove nanotechnology")).toBeInTheDocument();

    await userEvent.type(within(filters).getByLabelText("Search industries"), "construction");
    await userEvent.click(within(filters).getByRole("option", { name: /construction/ }));

    await waitFor(
      () =>
        expect(vi.mocked(strategyApi.putFilter).mock.calls.at(-1)![1].industries).toEqual([
          "nanotechnology",
          "construction",
        ]),
      { timeout: 2000 },
    );
  });

  it("summarises a closed panel as pills, each one removable on its own", async () => {
    renderPage();
    const filters = await screen.findByRole("region", { name: "Filters" });
    const header = within(filters).getByRole("button", { name: /^Industry/ });

    await userEvent.click(header);
    await userEvent.type(within(filters).getByLabelText("Search industries"), "oil");
    await userEvent.click(within(filters).getByRole("option", { name: /oil & energy/ }));
    await userEvent.type(within(filters).getByLabelText("Search industries"), "construction");
    await userEvent.click(within(filters).getByRole("option", { name: /construction/ }));
    await userEvent.click(header);

    expect(within(filters).getByLabelText("Remove oil & energy")).toBeInTheDocument();
    await userEvent.click(within(filters).getByLabelText("Remove construction"));

    await waitFor(
      () =>
        expect(vi.mocked(strategyApi.putFilter).mock.calls.at(-1)![1].industries).toEqual([
          "oil & energy",
        ]),
      { timeout: 2000 },
    );
  });

  it("suggests the industries beside the one chosen, and adds them to what is already selected", async () => {
    renderPage();
    const filters = await screen.findByRole("region", { name: "Filters" });
    await userEvent.click(within(filters).getByRole("button", { name: /^Industry/ }));

    // Nothing picked yet, so there is nothing to be adjacent *to*.
    expect(within(filters).queryByText("Adjacent Industries")).not.toBeInTheDocument();

    await userEvent.type(within(filters).getByLabelText("Search industries"), "oil");
    await userEvent.click(within(filters).getByRole("option", { name: /oil & energy/ }));
    expect(within(filters).getByText("Adjacent Industries")).toBeInTheDocument();

    await userEvent.click(within(filters).getByRole("button", { name: "construction" }));

    // The suggestion adds to the selection rather than replacing it — the results panel has to show
    // the union, which is the whole point of offering a neighbour.
    await waitFor(
      () =>
        expect(vi.mocked(strategyApi.putFilter).mock.calls.at(-1)![1].industries).toEqual([
          "oil & energy",
          "construction",
        ]),
      { timeout: 2000 },
    );
  });

  it("moves a taken suggestion out of the row, and widens it by what that industry is beside", async () => {
    renderPage();
    const filters = await screen.findByRole("region", { name: "Filters" });
    await userEvent.click(within(filters).getByRole("button", { name: /^Industry/ }));
    await userEvent.type(within(filters).getByLabelText("Search industries"), "oil");
    await userEvent.click(within(filters).getByRole("option", { name: /oil & energy/ }));

    // Its own sector's other leaves first, then the leaves of the sectors beside it.
    const chips = () =>
      within(within(filters).getByRole("group", { name: "Adjacent Industries" }))
        .getAllByRole("button")
        .map((chip) => chip.textContent);
    expect(chips()).toEqual(["utilities", "construction"]);

    await userEvent.click(within(filters).getByRole("button", { name: "construction" }));

    // Taken, so it is a selection now and no longer something to suggest.
    expect(chips()).toEqual(["utilities"]);
    await waitFor(
      () =>
        expect(vi.mocked(strategyApi.putFilter).mock.calls.at(-1)![1].industries).toEqual([
          "oil & energy",
          "construction",
        ]),
      { timeout: 2000 },
    );

    // And it is still the way back: releasing the pill returns it to the suggestions.
    await userEvent.click(within(filters).getByLabelText("Remove construction"));
    expect(chips()).toEqual(["utilities", "construction"]);
  });

  it("constrains nothing until Include keywords is ticked and a keyword is taken", async () => {
    renderPage();
    const filters = await screen.findByRole("region", { name: "Filters" });
    await userEvent.click(within(filters).getByRole("button", { name: /^Industry/ }));

    // Unticked, the box is not even there: every company the other axes reach still comes back.
    expect(within(filters).queryByLabelText("Search keywords")).not.toBeInTheDocument();
    expect(companiesApi.searchKeywords).not.toHaveBeenCalled();

    await userEvent.click(within(filters).getByRole("checkbox", { name: "Include keywords" }));

    await userEvent.type(within(filters).getByLabelText("Search keywords"), "s");
    expect(companiesApi.searchKeywords).not.toHaveBeenCalled();

    await userEvent.type(within(filters).getByLabelText("Search keywords"), "a");
    await userEvent.click(await within(filters).findByRole("option", { name: /saas/ }));

    await waitFor(
      () =>
        expect(vi.mocked(strategyApi.putFilter).mock.calls.at(-1)![1].keywords).toEqual(["saas"]),
      { timeout: 2000 },
    );
  });

  it("removes the right pill when an industry and a keyword are the same word", async () => {
    vi.mocked(strategyApi.getStrategy).mockResolvedValue(
      strategyOf({ ...EMPTY_FILTER, industries: ["construction"], keywords: ["construction"] }),
    );
    renderPage();
    const filters = await screen.findByRole("region", { name: "Filters" });

    // One accordion, so one closed-panel summary: two pills reading the same word, removing
    // different things.
    const pills = within(filters).getAllByLabelText("Remove construction");
    expect(pills).toHaveLength(2);
    await userEvent.click(pills[1]!);

    await waitFor(() => {
      const saved = vi.mocked(strategyApi.putFilter).mock.calls.at(-1)![1];
      expect(saved.keywords).toEqual([]);
      expect(saved.industries).toEqual(["construction"]);
    }, { timeout: 2000 });
  });

  it("clears the keywords it collected when Include keywords is unticked", async () => {
    vi.mocked(strategyApi.getStrategy).mockResolvedValue(
      strategyOf({ ...EMPTY_FILTER, keywords: ["saas"] }),
    );
    renderPage();
    const filters = await screen.findByRole("region", { name: "Filters" });
    await userEvent.click(within(filters).getByRole("button", { name: /^Industry/ }));

    // An empty list is the unticked state, so leaving the keywords behind would tick itself back on.
    await userEvent.click(within(filters).getByRole("checkbox", { name: "Include keywords" }));

    await waitFor(
      () => expect(vi.mocked(strategyApi.putFilter).mock.calls.at(-1)![1].keywords).toEqual([]),
      { timeout: 2000 },
    );
  });

  it("never takes a keyword the universe does not offer, however it is typed", async () => {
    renderPage();
    const filters = await screen.findByRole("region", { name: "Filters" });
    await userEvent.click(within(filters).getByRole("button", { name: /^Industry/ }));
    await userEvent.click(within(filters).getByRole("checkbox", { name: "Include keywords" }));

    await userEvent.type(within(filters).getByLabelText("Search keywords"), "nonesuch{Enter}");

    // A keyword the pipeline does not carry narrows to nothing while looking like it narrowed.
    await waitFor(() => expect(within(filters).getByText("No keyword matches that.")).toBeInTheDocument());
    expect(within(filters).queryByLabelText("Remove nonesuch")).not.toBeInTheDocument();
  });

  it("offers an Unknown revenue row, because most companies publish no figure", async () => {
    renderPage();
    const filters = await screen.findByRole("region", { name: "Filters" });

    // Scoped to the rail: the results table has a sortable "Revenue" header of the same name.
    await userEvent.click(within(filters).getByRole("button", { name: "Revenue" }));

    // Without it the 64,690 companies with no revenue are unreachable by this axis.
    expect(within(filters).getByRole("checkbox", { name: /Unknown/ })).toBeInTheDocument();
  });

  it("renders each axis with the control its values deserve, not chips everywhere", async () => {
    renderPage();
    const filters = await screen.findByRole("region", { name: "Filters" });

    // Location is six countries and reads as pills, named and nothing else — an axis that shallow
    // has nothing a count would decide.
    expect(within(filters).getByRole("button", { name: "Qatar" })).toBeInTheDocument();
    expect(within(filters).queryByRole("checkbox", { name: /Qatar/ })).not.toBeInTheDocument();

    // Employees is an ordered axis of eleven bands, so it is a checkbox list. Pills would lose the
    // order, which is the only thing that makes the list readable.
    await userEvent.click(within(filters).getByRole("button", { name: "# Employees" }));
    expect(within(filters).getByRole("checkbox", { name: /1001-2000/ })).toBeInTheDocument();
  });

  it("keeps a band row's label identical whether it is ticked or not", async () => {
    renderPage();
    const filters = await screen.findByRole("region", { name: "Filters" });
    await userEvent.click(within(filters).getByRole("button", { name: "# Employees" }));

    const row = within(filters).getByRole("checkbox", { name: /1001-2000/ });
    const label = within(row).getByText("1001-2000");
    const before = label.className;

    await userEvent.click(row);

    // Only the box changes. A list where ticked rows also recolour reads as two kinds of row, and
    // the eye has to re-scan to find the checked ones instead of following the checkmarks down.
    await waitFor(() => expect(row).toHaveAttribute("aria-checked", "true"));
    expect(label.className).toBe(before);
  });

  it("a custom range replaces the band selection rather than narrowing it further", async () => {
    renderPage();
    const filters = await screen.findByRole("region", { name: "Filters" });
    await userEvent.click(within(filters).getByRole("button", { name: "# Employees" }));

    await userEvent.click(within(filters).getByRole("checkbox", { name: /1001-2000/ }));
    await userEvent.click(within(filters).getByRole("radio", { name: "Custom Range" }));
    await userEvent.type(within(filters).getByLabelText("Min"), "250");

    await waitFor(
      () => expect(vi.mocked(strategyApi.putFilter).mock.calls.at(-1)![1].employeeRange).toEqual({
        min: 250,
        max: null,
      }),
      { timeout: 2000 },
    );
    // Ticked bands cannot survive the mode switch, or the stored filter would say two things.
    expect(vi.mocked(strategyApi.putFilter).mock.calls.at(-1)![1].employeeBands).toEqual([]);
  });

  it("counts a custom range as an active axis, but not the mode switch on its own", async () => {
    renderPage();
    const filtersButton = await screen.findByRole("button", { name: /Show Filters|Hide Filters/ });
    const filters = await screen.findByRole("region", { name: "Filters" });
    await userEvent.click(within(filters).getByRole("button", { name: "# Employees" }));

    // Entering Custom Range emits an empty range, which the server normalises away. Counting that
    // would put the badge at 1 over an unfiltered table.
    await userEvent.click(within(filters).getByRole("radio", { name: "Custom Range" }));
    expect(within(filtersButton).getByText("0")).toBeInTheDocument();

    // A typed bound does narrow the scope, and the badge said 0 while it did — the accordion's own
    // tag showed the range all along, so two counters on one screen disagreed.
    await userEvent.type(within(filters).getByLabelText("Min"), "250");
    await waitFor(() => expect(within(filtersButton).getByText("1")).toBeInTheDocument());
  });

  it("counts the axes that carry a selection, not the chips", async () => {
    renderPage();
    const filtersButton = await screen.findByRole("button", { name: /Show Filters|Hide Filters/ });
    expect(within(filtersButton).getByText("0")).toBeInTheDocument();

    await userEvent.click(await screen.findByRole("button", { name: /Qatar/ }));
    await userEvent.click(await screen.findByRole("button", { name: /United Arab Emirates/ }));

    // Two chips on one axis is still one active filter.
    await waitFor(() => expect(within(filtersButton).getByText("1")).toBeInTheDocument());
  });

  it("does not claim an empty result while the first page is still loading", async () => {
    let release!: (page: CompanyPage) => void;
    vi.mocked(strategyApi.getCompanies).mockReturnValue(
      new Promise<CompanyPage>((resolve) => {
        release = resolve;
      }),
    );
    renderPage();

    // "0 results" beside a loading skeleton states as fact that nothing matched, at the moment the
    // screen does not yet know — the table and the bar contradicting each other.
    expect(await screen.findByRole("button", { name: "Next page" })).toBeInTheDocument();
    expect(screen.queryByText("0 results")).not.toBeInTheDocument();

    release(pageOf());
    expect(await screen.findByText("1 - 1 of 1")).toBeInTheDocument();
  });

  it("renders a 403 as an error rather than as an empty market", async () => {
    vi.mocked(strategyApi.getCompanies).mockRejectedValue(new Error("forbidden"));
    renderPage();

    // The count is the tell: "no companies match" states as fact a number the caller could not read.
    expect(await screen.findByText(/could not be loaded/i)).toBeInTheDocument();
    expect(screen.queryByText(/No companies match/i)).not.toBeInTheDocument();
  });

  it("bars a company through its own endpoint, and says how many are barred", async () => {
    vi.mocked(strategyApi.getStrategy).mockResolvedValue({
      ...strategyOf(),
      offLimits: [
        {
          apolloAccountId: "x1",
          companyName: "Acme Corp",
          industry: null,
          companyCity: null,
          companyCountry: null,
          logoUrl: null,
        },
      ],
    });
    vi.mocked(strategyApi.putOffLimits).mockResolvedValue(strategyOf());
    renderPage();
    const filters = await screen.findByRole("region", { name: "Filters" });

    await userEvent.click(within(filters).getByRole("button", { name: /^Off-limits/ }));
    expect(within(filters).getByText("EXCLUDED (1)")).toBeInTheDocument();

    await userEvent.click(within(filters).getByRole("button", { name: "Remove Acme Corp" }));

    // Off-limits is a decision, not a draft: it writes immediately rather than through the timer.
    await waitFor(() => expect(strategyApi.putOffLimits).toHaveBeenCalledWith("p1", []));
    expect(strategyApi.putFilter).not.toHaveBeenCalled();
  });

  it("closes the suggestion list once a company is barred, rather than covering the chips it joined", async () => {
    vi.mocked(companiesApi.searchCompanies).mockResolvedValue({
      companies: [
        {
          apolloAccountId: "x1",
          companyName: "Acme Corp",
          industry: null,
          companyCity: null,
          companyCountry: null,
          website: null,
          logoUrl: null,
          numEmployees: null,
        },
      ],
    });
    vi.mocked(strategyApi.putOffLimits).mockResolvedValue(strategyOf());
    renderPage();
    const filters = await screen.findByRole("region", { name: "Filters" });
    await userEvent.click(within(filters).getByRole("button", { name: /^Off-limits/ }));

    await userEvent.type(within(filters).getByLabelText("Search companies"), "Acme");
    await userEvent.click(await within(filters).findByRole("option", { name: /Acme Corp/ }));

    // keepPreviousData keeps serving the last query's rows after a pick clears the box, so a list
    // left open sits over the EXCLUDED chips — including the one just added, and its remove button.
    await waitFor(() =>
      expect(within(filters).getByRole("combobox")).toHaveAttribute("aria-expanded", "false"),
    );
    expect(within(filters).queryByRole("option")).not.toBeInTheDocument();
  });

  it("flushes the pending filter before saving a search", async () => {
    vi.mocked(strategyApi.saveSearch).mockResolvedValue(savedSearchOf({ name: "Fast save" }));
    renderPage();

    await userEvent.click(await screen.findByRole("button", { name: /Qatar/ }));
    await userEvent.click(screen.getByRole("button", { name: /Save Search/ }));
    await userEvent.type(screen.getByLabelText("Name this search"), "Fast save");
    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    // The request carries only a name — the server snapshots the *stored* filter. Saving inside the
    // 700ms debounce recorded the scope as it was before the chip click, silently, and stayed wrong
    // for every later load of that search.
    await waitFor(() => expect(strategyApi.saveSearch).toHaveBeenCalled());
    expect(vi.mocked(strategyApi.putFilter).mock.invocationCallOrder[0]).toBeLessThan(
      vi.mocked(strategyApi.saveSearch).mock.invocationCallOrder[0]!,
    );
  });

  it("flushes the pending filter before adding everything in scope", async () => {
    vi.mocked(triageApi.addAllInScope).mockResolvedValue({ added: 12, skipped: 0 });
    renderPage();

    await userEvent.click(await screen.findByRole("button", { name: /Qatar/ }));
    await userEvent.click(screen.getByRole("button", { name: /Add all to Universe/ }));

    // "Add all" acts on the stored filter; a debounced edit still in the timer would mean the
    // server adds companies from the filter as it was two chips ago.
    await waitFor(() => expect(triageApi.addAllInScope).toHaveBeenCalled());
    expect(vi.mocked(strategyApi.putFilter).mock.invocationCallOrder[0]).toBeLessThan(
      vi.mocked(triageApi.addAllInScope).mock.invocationCallOrder[0]!,
    );
  });

  it("shows the server's own numbers when a bulk add is refused as too large", async () => {
    vi.mocked(triageApi.addAllInScope).mockRejectedValue(
      new ApiRequestError({
        code: "BULK_ADD_SCOPE_TOO_LARGE",
        detail: "3,000 companies match this filter. You can add 200 at a time — narrow it and try again.",
        status: 409,
        correlationId: "test",
      }),
    );
    renderPage();

    await userEvent.click(await screen.findByRole("button", { name: /Add all to Universe/ }));

    // The code is deliberately absent from MESSAGES so messageFor falls through to the server's
    // detail: no fixed sentence here could name how many matched or how many may be added.
    expect(await screen.findByText(/3,000 companies match this filter/i)).toBeInTheDocument();
  });

  it("saves a search under a name and lets it be loaded back", async () => {
    vi.mocked(strategyApi.saveSearch).mockResolvedValue(savedSearchOf());
    renderPage();

    await userEvent.click(await screen.findByRole("button", { name: /Save Search/ }));
    await userEvent.type(screen.getByLabelText("Name this search"), "GCC energy");
    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() =>
      expect(strategyApi.saveSearch).toHaveBeenCalledWith("p1", "GCC energy", "SHARED"),
    );
  });

  it("saves under the tier the viewer picked", async () => {
    vi.mocked(strategyApi.saveSearch).mockResolvedValue(savedSearchOf({ visibility: "PRIVATE" }));
    renderPage();

    await userEvent.click(await screen.findByRole("button", { name: /Save Search/ }));
    await userEvent.type(screen.getByLabelText("Name this search"), "Scratch");
    await userEvent.click(screen.getByRole("radio", { name: "Only me" }));
    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() =>
      expect(strategyApi.saveSearch).toHaveBeenCalledWith("p1", "Scratch", "PRIVATE"),
    );
  });

  it("splits the dropdown into the viewer's own searches and the mandate's", async () => {
    vi.mocked(strategyApi.getStrategy).mockResolvedValue(
      strategyOf(EMPTY_FILTER, [
        savedSearchOf({ id: "s1", name: "My scratch", visibility: "PRIVATE" }),
        savedSearchOf({
          id: "s2",
          name: "Team scope",
          createdById: "u2",
          createdByName: "Omar Farouk",
        }),
      ]),
    );
    renderPage();

    await userEvent.click(await screen.findByRole("button", { name: /Save Search/ }));

    // Shared opens first: it is the mandate's list, and a private search is by definition not
    // something a teammate is looking for here.
    // The row's accessible name carries its provenance line too, so these match on the prefix.
    expect(screen.getByRole("button", { name: /^Team scope/ })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /^My scratch/ })).not.toBeInTheDocument();
    expect(screen.getByText(/Omar Farouk/)).toBeInTheDocument();

    await userEvent.click(screen.getByRole("radio", { name: /^Mine/ }));
    expect(screen.getByRole("button", { name: /^My scratch/ })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /^Team scope/ })).not.toBeInTheDocument();
  });

  it("marks the saved search the sidebar is currently showing", async () => {
    vi.mocked(strategyApi.getStrategy).mockResolvedValue(
      strategyOf({ ...EMPTY_FILTER, industries: ["utilities", "oil & energy"] }, [
        // Same two industries, clicked in the other order: the marker compares filters as sets, so a
        // search loaded back must not look inactive because of the order its chips went on.
        savedSearchOf({
          id: "s1",
          name: "Qatar only",
          filter: { ...EMPTY_FILTER, industries: ["oil & energy", "utilities"] },
        }),
        savedSearchOf({ id: "s2", name: "Everything" }),
      ]),
    );
    renderPage();

    await userEvent.click(await screen.findByRole("button", { name: /Save Search/ }));

    expect(screen.getByRole("button", { name: /Qatar only.*Active/s })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /^Everything/s })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Everything.*Active/s })).not.toBeInTheDocument();
  });

  it("renames a saved search in place", async () => {
    vi.mocked(strategyApi.getStrategy).mockResolvedValue(
      strategyOf(EMPTY_FILTER, [savedSearchOf({ name: "GCC energy" })]),
    );
    vi.mocked(strategyApi.patchSearch).mockResolvedValue(savedSearchOf({ name: "GCC utilities" }));
    renderPage();

    await userEvent.click(await screen.findByRole("button", { name: /Save Search/ }));
    await userEvent.click(screen.getByRole("button", { name: "Rename GCC energy" }));
    await userEvent.clear(screen.getByLabelText("Rename GCC energy"));
    await userEvent.type(screen.getByLabelText("Rename GCC energy"), "  GCC utilities  {Enter}");

    await waitFor(() =>
      expect(strategyApi.patchSearch).toHaveBeenCalledWith("p1", "s1", { name: "GCC utilities" }),
    );
  });

  it("opens on Mine when every saved search is the viewer's own private one", async () => {
    vi.mocked(strategyApi.getStrategy).mockResolvedValue(
      strategyOf(EMPTY_FILTER, [savedSearchOf({ name: "My scratch", visibility: "PRIVATE" })]),
    );
    renderPage();

    await userEvent.click(await screen.findByRole("button", { name: /Save Search/ }));

    // The trigger badge counts every search the viewer can see, so opening on an empty Shared list
    // under a badge reading 1 told them their own search was missing.
    expect(screen.getByRole("button", { name: /^My scratch/ })).toBeInTheDocument();
  });

  it("leaves a half-typed rename behind when the menu closes", async () => {
    vi.mocked(strategyApi.getStrategy).mockResolvedValue(
      strategyOf(EMPTY_FILTER, [savedSearchOf({ name: "GCC energy" })]),
    );
    renderPage();

    await userEvent.click(await screen.findByRole("button", { name: /Save Search/ }));
    await userEvent.click(screen.getByRole("button", { name: "Rename GCC energy" }));
    await userEvent.type(screen.getByLabelText("Rename GCC energy"), "half typed");
    // Clicking outside is what closes the Popover — Escape now stops at the rename input.
    await userEvent.click(document.body);
    await userEvent.click(screen.getByRole("button", { name: /Save Search/ }));

    // The rename lived on the menu once, which outlives the panel — so reopening dropped the reader
    // into an autofocused editor for a row they had moved on from, holding text they never saved.
    expect(screen.queryByRole("textbox", { name: "Rename GCC energy" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /^GCC energy/ })).toBeInTheDocument();
    expect(strategyApi.patchSearch).not.toHaveBeenCalled();
  });

  it("Escape leaves the rename input without closing the whole menu", async () => {
    vi.mocked(strategyApi.getStrategy).mockResolvedValue(
      strategyOf(EMPTY_FILTER, [savedSearchOf({ name: "GCC energy" })]),
    );
    renderPage();

    await userEvent.click(await screen.findByRole("button", { name: /Save Search/ }));
    await userEvent.click(screen.getByRole("button", { name: "Rename GCC energy" }));
    await userEvent.keyboard("{Escape}");

    // Popover closes on Escape from the document, so without stopPropagation the innermost cancel
    // took the dropdown down with it.
    expect(screen.queryByRole("textbox", { name: "Rename GCC energy" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /^GCC energy/ })).toBeInTheDocument();
  });

  it("moves the viewer's own search between tiers, and offers that on nobody else's", async () => {
    vi.mocked(strategyApi.getStrategy).mockResolvedValue(
      strategyOf(EMPTY_FILTER, [
        savedSearchOf({ id: "s1", name: "Team scope" }),
        savedSearchOf({
          id: "s2",
          name: "Omar's scope",
          createdById: "u2",
          createdByName: "Omar Farouk",
        }),
      ]),
    );
    vi.mocked(strategyApi.patchSearch).mockResolvedValue(savedSearchOf({ visibility: "PRIVATE" }));
    renderPage();

    await userEvent.click(await screen.findByRole("button", { name: /Save Search/ }));

    // Only the author moves a search between tiers, and the server refuses it for anyone else — so
    // the affordance is not offered where it would only produce a 403.
    expect(
      screen.queryByRole("button", { name: "Make Omar's scope private" }),
    ).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Make Team scope private" }));

    // No name in the patch. Sending the cached one wrote it back, so a toggle clicked against a row
    // a teammate had since renamed silently reverted their rename.
    await waitFor(() =>
      expect(strategyApi.patchSearch).toHaveBeenCalledWith("p1", "s1", { visibility: "PRIVATE" }),
    );
  });

  it("keeps the row's actions reachable by keyboard", async () => {
    vi.mocked(strategyApi.getStrategy).mockResolvedValue(
      strategyOf(EMPTY_FILTER, [savedSearchOf({ name: "GCC energy" })]),
    );
    renderPage();

    await userEvent.click(await screen.findByRole("button", { name: /Save Search/ }));
    const rename = screen.getByRole("button", { name: "Rename GCC energy" });
    rename.focus();

    // The actions are opacity-0 until the row is hovered, which a keyboard reader never does — so
    // without a focus escape hatch they tab through four invisible buttons, one of which deletes.
    expect(rename.className).toContain("group-focus-within:opacity-100");
    expect(rename).toHaveFocus();
  });

  it("flushes the pending filter before re-capturing it onto a saved search", async () => {
    vi.mocked(strategyApi.getStrategy).mockResolvedValue(
      strategyOf(EMPTY_FILTER, [savedSearchOf({ name: "GCC energy" })]),
    );
    vi.mocked(strategyApi.overwriteSearch).mockResolvedValue(savedSearchOf());
    renderPage();

    await userEvent.click(await screen.findByRole("button", { name: /Qatar/ }));
    await userEvent.click(screen.getByRole("button", { name: /Save Search/ }));
    await userEvent.click(
      screen.getByRole("button", { name: "Update GCC energy to the current filter" }),
    );

    // Bodyless, like the save: the server re-reads the *stored* filter, so an edit still sitting in
    // the 700ms debounce would be captured as the scope from before the last chip click.
    await waitFor(() => expect(strategyApi.overwriteSearch).toHaveBeenCalledWith("p1", "s1"));
    expect(vi.mocked(strategyApi.putFilter).mock.invocationCallOrder[0]).toBeLessThan(
      vi.mocked(strategyApi.overwriteSearch).mock.invocationCallOrder[0]!,
    );
  });

  it("returns to the first page when the filter changes", async () => {
    vi.mocked(strategyApi.getCompanies).mockResolvedValue(pageOf({ totalCount: 200 }));
    renderPage();

    await userEvent.click(await screen.findByRole("button", { name: "Next page" }));
    await waitFor(() =>
      expect(vi.mocked(strategyApi.getCompanies).mock.calls.at(-1)![1]).toBe(1),
    );

    await userEvent.click(screen.getByRole("button", { name: /Qatar/ }));

    // Staying on page 4 of a filter that now matches two companies shows an empty table over a
    // non-empty result.
    await waitFor(() => expect(vi.mocked(strategyApi.getCompanies).mock.calls.at(-1)![1]).toBe(0));
  });
  it("sorts on the server rather than reordering the page it happens to hold", async () => {
    vi.mocked(strategyApi.getCompanies).mockResolvedValue(pageOf({ totalCount: 4000 }));
    renderPage();

    // Scoped to the table: "Revenue" also names a filter accordion, and the two must not be one
    // control by accident.
    const table = await screen.findByRole("table", { name: "Companies" });
    await userEvent.click(within(table).getByRole("button", { name: /Revenue/ }));

    // The table holds 25 of tens of thousands. Sorting those 25 client-side would reorder the page
    // while claiming to have ordered the result, so a header click has to become a new query.
    await waitFor(() =>
      expect(vi.mocked(strategyApi.getCompanies).mock.calls.at(-1)![4]).toEqual({
        field: "revenue",
        direction: "desc",
      }),
    );

    await userEvent.click(within(table).getByRole("button", { name: /Revenue/ }));

    await waitFor(() =>
      expect(vi.mocked(strategyApi.getCompanies).mock.calls.at(-1)![4]).toEqual({
        field: "revenue",
        direction: "asc",
      }),
    );
  });

  it("never sorts by a column the server has no ORDER BY for", async () => {
    renderPage();
    const table = await screen.findByRole("table", { name: "Companies" });

    // Notes is short_description, which the sort allowlist deliberately omits — alphabetising a
    // description answers no question. A header that looked clickable and did nothing would be worse.
    expect(within(table).getByText("Notes")).toBeInTheDocument();
    expect(within(table).queryByRole("button", { name: /Notes/ })).not.toBeInTheDocument();
  });

  it("hides a column on request and remembers it for this mandate", async () => {
    const { unmount } = renderPage();
    expect(within(await screen.findByRole("table", { name: "Companies" })).getByText("Sector"))
      .toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: /Columns/ }));
    await userEvent.click(screen.getByRole("checkbox", { name: "Sector" }));

    const table = screen.getByRole("table", { name: "Companies" });
    expect(within(table).queryByText("Sector")).not.toBeInTheDocument();
    // Hiding a column is presentation: the rows stay, and the server is not re-asked.
    expect(within(table).getByText("ACWA Power")).toBeInTheDocument();

    unmount();
    renderPage(new QueryClient({ defaultOptions: { queries: { retry: false } } }));

    // A layout that resets on every visit is a layout nobody bothers to set.
    const reopened = await screen.findByRole("table", { name: "Companies" });
    expect(within(reopened).queryByText("Sector")).not.toBeInTheDocument();
  });

  it("does not offer to hide the company name or the actions", async () => {
    renderPage();
    await screen.findByRole("table", { name: "Companies" });

    await userEvent.click(screen.getByRole("button", { name: /Columns/ }));

    // The name is the row's identity and the add button is the only thing this screen is for.
    // Hiding either leaves a table of figures about nothing, or one you can only read.
    expect(screen.queryByRole("checkbox", { name: "Company" })).not.toBeInTheDocument();
    expect(screen.queryByRole("checkbox", { name: "Actions" })).not.toBeInTheDocument();
    expect(screen.getByRole("checkbox", { name: "Country" })).toBeInTheDocument();
  });

  it("keeps City and Founded one tick away rather than on screen", async () => {
    renderPage();
    const table = await screen.findByRole("table", { name: "Companies" });

    // Both are real Apollo fields the server sorts by; the wireframe's table is eight columns wide,
    // and adding two more squeezes the eight that earn their place.
    expect(within(table).queryByText("Riyadh")).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: /Columns/ }));
    await userEvent.click(screen.getByRole("checkbox", { name: "City" }));

    expect(within(screen.getByRole("table", { name: "Companies" })).getByText("Riyadh"))
      .toBeInTheDocument();
  });
});
