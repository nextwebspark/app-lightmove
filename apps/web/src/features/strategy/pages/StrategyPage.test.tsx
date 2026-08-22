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
import type { CompanyPage, Facets, Strategy, StrategyFilter } from "../api/types";
import * as triageApi from "../../triage/api/triageApi";
import { StrategyPage } from "./StrategyPage";

vi.mock("../api/strategyApi", async (importOriginal) => ({
  ...(await importOriginal<typeof strategyApi>()),
  getStrategy: vi.fn(),
  putFilter: vi.fn(),
  getCompanies: vi.fn(),
  saveSearch: vi.fn(),
  deleteSearch: vi.fn(),
  putOffLimits: vi.fn(),
}));
vi.mock("../../triage/api/triageApi", async (importOriginal) => ({
  ...(await importOriginal<typeof triageApi>()),
  addToUniverse: vi.fn(),
  addAllInScope: vi.fn(),
}));
vi.mock("../api/companiesApi", async (importOriginal) => ({
  ...(await importOriginal<typeof companiesApi>()),
  getFacets: vi.fn(),
}));

const project = { id: "p1", positionTitle: "CFO" } as Project;

const EMPTY_FILTER: StrategyFilter = {
  industries: [],
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
      count: 3,
      industries: [
        { value: "oil & energy", label: "oil & energy", count: 2 },
        { value: "utilities", label: "utilities", count: 1 },
      ],
    },
    {
      name: "Construction",
      count: 5,
      industries: [{ value: "construction", label: "construction", count: 5 }],
    },
  ],
  marketSegments: [{ value: "B2B", label: "B2B", count: 40 }],
  countries: [
    { value: "United Arab Emirates", label: "United Arab Emirates", count: 37154 },
    { value: "Qatar", label: "Qatar", count: 4609 },
  ],
  employeeBands: [
    { value: "1001-2000", label: "1001-2000", count: 2022 },
    { value: "2001-5000", label: "2001-5000", count: 640 },
  ],
  revenueBands: [
    { value: "1b-5b", label: "$1B - $5B", count: 289 },
    { value: "unknown", label: "Unknown", count: 64690 },
  ],
};

const strategyOf = (filter: StrategyFilter = EMPTY_FILTER): Strategy => ({
  filter,
  offLimits: [],
  searches: [],
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
    await waitFor(() =>
      expect(within(filters).getByText(/counts are not available to you/i)).toBeInTheDocument(),
    );
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

  it("selecting a sector stores its industries, never the sector name", async () => {
    renderPage();
    const filters = await screen.findByRole("region", { name: "Filters" });

    await userEvent.click(within(filters).getByRole("button", { name: "Industry" }));
    await userEvent.click(within(filters).getByRole("checkbox", { name: /Energy & Utilities/ }));

    // No Apply step: the click is the decision, and the filter's own autosave coalesces a burst.
    await waitFor(() => expect(strategyApi.putFilter).toHaveBeenCalled(), { timeout: 2000 });
    // Storing the group would silently widen this mandate the day the taxonomy is re-tuned.
    expect(vi.mocked(strategyApi.putFilter).mock.calls.at(-1)![1].industries).toEqual([
      "oil & energy",
      "utilities",
    ]);
  });

  it("browsing a sector takes nothing when sub-industries are not included", async () => {
    renderPage();
    const filters = await screen.findByRole("region", { name: "Filters" });
    await userEvent.click(within(filters).getByRole("button", { name: "Industry" }));

    await userEvent.click(within(filters).getByRole("checkbox", { name: "Include Sub-Industries" }));
    await userEvent.click(within(filters).getByRole("checkbox", { name: /Energy & Utilities/ }));

    // Unticked, a sector is a lens rather than a selection — "we're looking at Energy" is not the
    // same claim as "we want all of Energy", and only the second one belongs in the filter.
    expect(within(filters).getByRole("checkbox", { name: /oil & energy/ })).toBeInTheDocument();
    expect(strategyApi.putFilter).not.toHaveBeenCalled();

    await userEvent.click(within(filters).getByRole("checkbox", { name: /oil & energy/ }));

    await waitFor(() => expect(strategyApi.putFilter).toHaveBeenCalled(), { timeout: 2000 });
    expect(vi.mocked(strategyApi.putFilter).mock.calls.at(-1)![1].industries).toEqual([
      "oil & energy",
    ]);
  });

  it("suggests the sectors beside the one chosen, and adds them to what is already selected", async () => {
    renderPage();
    const filters = await screen.findByRole("region", { name: "Filters" });
    await userEvent.click(within(filters).getByRole("button", { name: "Industry" }));

    // Nothing open yet, so there is nothing to be adjacent *to*.
    expect(within(filters).queryByText("Adjacent Industries")).not.toBeInTheDocument();

    await userEvent.click(within(filters).getByRole("checkbox", { name: /Energy & Utilities/ }));
    expect(within(filters).getByText("Adjacent Industries")).toBeInTheDocument();

    const chip = within(filters).getByRole("button", { name: /Construction/ });
    await userEvent.click(chip);

    // The suggestion adds to the selection rather than replacing it — the results panel has to show
    // the union, which is the whole point of offering a neighbour.
    await waitFor(
      () =>
        expect(vi.mocked(strategyApi.putFilter).mock.calls.at(-1)![1].industries).toEqual([
          "oil & energy",
          "utilities",
          "construction",
        ]),
      { timeout: 2000 },
    );
  });

  it("an adjacent chip stays put once taken, so several can be picked in a row", async () => {
    renderPage();
    const filters = await screen.findByRole("region", { name: "Filters" });
    await userEvent.click(within(filters).getByRole("button", { name: "Industry" }));
    await userEvent.click(within(filters).getByRole("checkbox", { name: /Energy & Utilities/ }));

    const chip = within(filters).getByRole("button", { name: /Construction/ });
    await userEvent.click(chip);

    // Dropping a chip the moment it is used answers the click by deleting the thing clicked, and
    // moves whatever the consultant was about to press second.
    expect(within(filters).getByRole("button", { name: /Construction/ })).toHaveAttribute(
      "aria-pressed",
      "true",
    );

    // And it is still the way back: a suggestion taken by accident has to be releasable.
    await userEvent.click(within(filters).getByRole("button", { name: /Construction/ }));
    await waitFor(
      () =>
        expect(vi.mocked(strategyApi.putFilter).mock.calls.at(-1)![1].industries).toEqual([
          "oil & energy",
          "utilities",
        ]),
      { timeout: 2000 },
    );
  });

  it("searches the sector list by the industries inside it, not only by its name", async () => {
    renderPage();
    const filters = await screen.findByRole("region", { name: "Filters" });
    await userEvent.click(within(filters).getByRole("button", { name: "Industry" }));

    // "utilities" is a label filed under Energy & Utilities; a consultant should not have to know
    // where we filed it to find it.
    await userEvent.type(within(filters).getByLabelText("Search industries"), "oil");

    expect(within(filters).getByRole("checkbox", { name: /Energy & Utilities/ })).toBeInTheDocument();
    expect(within(filters).queryByRole("checkbox", { name: /Construction/ })).not.toBeInTheDocument();
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

    // Location is six countries and reads as pills; the count lives on the pill itself.
    expect(within(filters).getByRole("button", { name: /Qatar/ })).toBeInTheDocument();
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

  it("counts the axes that carry a selection, not the chips", async () => {
    renderPage();
    const filtersButton = await screen.findByRole("button", { name: /Show Filters|Hide Filters/ });
    expect(within(filtersButton).getByText("0")).toBeInTheDocument();

    await userEvent.click(await screen.findByRole("button", { name: /Qatar/ }));
    await userEvent.click(await screen.findByRole("button", { name: /United Arab Emirates/ }));

    // Two chips on one axis is still one active filter.
    await waitFor(() => expect(within(filtersButton).getByText("1")).toBeInTheDocument());
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

    await userEvent.click(within(filters).getByRole("button", { name: /Off-limits/ }));
    expect(within(filters).getByText("EXCLUDED (1)")).toBeInTheDocument();

    await userEvent.click(within(filters).getByRole("button", { name: "Remove Acme Corp" }));

    // Off-limits is a decision, not a draft: it writes immediately rather than through the timer.
    await waitFor(() => expect(strategyApi.putOffLimits).toHaveBeenCalledWith("p1", []));
    expect(strategyApi.putFilter).not.toHaveBeenCalled();
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
    const saved = { id: "s1", name: "GCC energy", filter: EMPTY_FILTER, createdAt: "2026-08-20" };
    vi.mocked(strategyApi.saveSearch).mockResolvedValue(saved);
    renderPage();

    await userEvent.click(await screen.findByRole("button", { name: /Save Search/ }));
    await userEvent.type(screen.getByLabelText("Name this search"), "GCC energy");
    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => expect(strategyApi.saveSearch).toHaveBeenCalledWith("p1", "GCC energy"));
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
