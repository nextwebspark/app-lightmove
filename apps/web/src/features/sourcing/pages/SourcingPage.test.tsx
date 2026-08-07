import {
  MutationObserver,
  QueryClient,
  QueryClientProvider,
} from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Outlet, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Project } from "../../projects/api/types";
import * as sourcingApi from "../api/sourcingApi";
import type { CompanyResult, SourcingResponse } from "../api/types";
import { SourcingPage } from "./SourcingPage";

vi.mock("../api/sourcingApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/sourcingApi")>()),
  getSourcingCompanies: vi.fn(),
}));

/** jsdom has no IntersectionObserver; capture the callback so tests can fire it manually to
 *  simulate the sentinel scrolling into view. */
let observerCallback: IntersectionObserverCallback | null = null;
class IntersectionObserverMock implements IntersectionObserver {
  readonly root = null;
  readonly rootMargin = "";
  readonly scrollMargin = "";
  readonly thresholds: ReadonlyArray<number> = [];
  #callback: IntersectionObserverCallback;
  constructor(callback: IntersectionObserverCallback) {
    this.#callback = callback;
    observerCallback = callback;
  }
  observe() {}
  unobserve() {}
  // A disconnected observer delivers nothing — without this the tests can fire a callback the page
  // has already torn down, and a screen that stops observing looks exactly like one that never did.
  disconnect() {
    if (observerCallback === this.#callback) {
      observerCallback = null;
    }
  }
  takeRecords(): IntersectionObserverEntry[] {
    return [];
  }
}
vi.stubGlobal("IntersectionObserver", IntersectionObserverMock);

/** jsdom has no ResizeObserver either. Without one the table never measures its width, so it can only
 *  ever render the fits-on-screen branch — the pinned/overflow path would go untested entirely. */
let observedElements: Element[] = [];
let resizeCallback: ResizeObserverCallback | null = null;
class ResizeObserverMock implements ResizeObserver {
  constructor(callback: ResizeObserverCallback) {
    resizeCallback = callback;
  }
  observe(element: Element) {
    observedElements.push(element);
  }
  unobserve() {}
  disconnect() {}
}
vi.stubGlobal("ResizeObserver", ResizeObserverMock);

/** Report a wrapper width, which is what decides between proportional and literal column widths. */
function reportTableWidth(width: number) {
  resizeCallback?.(
    observedElements.map(
      (target) => ({ target, contentRect: { width } }) as ResizeObserverEntry,
    ),
    new ResizeObserverMock(() => {}),
  );
}

function triggerSentinelIntersect() {
  observerCallback?.(
    [{ isIntersecting: true } as IntersectionObserverEntry],
    new IntersectionObserverMock(() => {}),
  );
}

const project: Project = {
  id: "p1",
  clientId: "c1",
  clientName: "Meridian Energy Group",
  positionTitle: "Head of Retail",
  stage: "BRIEF",
  health: "OK",
  targetDate: null,
  team: [],
  representatives: [],
  companies: 0,
  candidates: 0,
  createdAt: "2026-07-01T00:00:00Z",
};

function company(overrides: Partial<CompanyResult> = {}): CompanyResult {
  return {
    id: 1,
    name: "Alpha Retail",
    domain: "alpha.com",
    website: "alpha.com",
    linkedinUrl: "linkedin.com/company/alpha",
    logo: "https://cdn.example.com/alpha.png",
    slogan: "Retail, done right",
    description: "A regional retail group.",
    sector: "Retail",
    industryTags: ["Grocery Retail"],
    specialties: ["Own-brand"],
    country: "AE",
    location: "Dubai, UAE",
    employeeRange: "1-10",
    revenueRange: "<5M",
    founded: 1998,
    ownership: "Privately Held",
    ipoStatus: "Private",
    orgType: "Company",
    matchTier: "DIRECT",
    ...overrides,
  };
}

function page(overrides: Partial<SourcingResponse> = {}): SourcingResponse {
  return {
    companies: [
      company(),
      company({
        id: 2,
        name: "Bravo Retail",
        sector: "Wholesale",
        employeeRange: "11-50",
        revenueRange: "5M-25M",
        location: "Riyadh, Saudi Arabia",
        country: "SA",
        matchTier: "ADJACENT",
      }),
    ],
    totalCount: 2,
    page: 0,
    size: 25,
    ...overrides,
  };
}

const renderPage = (
  client = new QueryClient({ defaultOptions: { queries: { retry: false } } }),
) =>
  render(
    <MemoryRouter initialEntries={["/"]}>
      <QueryClientProvider client={client}>
        <Routes>
          <Route element={<Outlet context={{ project }} />}>
            <Route path="/" element={<SourcingPage />} />
          </Route>
        </Routes>
      </QueryClientProvider>
    </MemoryRouter>,
  );

/** The picker's checkbox for one column, by its header label. */
const columnToggle = (label: string) => screen.queryByRole("checkbox", { name: label });

const openPicker = () => userEvent.click(screen.getByRole("button", { name: "Columns" }));

describe("SourcingPage — the filtered company table", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    localStorage.clear();
    observedElements = [];
    resizeCallback = null;
  });

  it("renders the matching companies as a table", async () => {
    vi.mocked(sourcingApi.getSourcingCompanies).mockResolvedValue(page());
    renderPage();

    expect(await screen.findByText("Alpha Retail")).toBeInTheDocument();
    expect(screen.getByText("Bravo Retail")).toBeInTheDocument();
    expect(
      screen.getByRole("columnheader", { name: /Company/ }),
    ).toBeInTheDocument();
    expect(screen.getByText("Dubai, UAE")).toBeInTheDocument();
  });

  it("shows each company's logo beside its name, falling back to the initial", async () => {
    vi.mocked(sourcingApi.getSourcingCompanies).mockResolvedValue(
      page({
        companies: [company(), company({ id: 2, name: "Bravo Retail", logo: null })],
        totalCount: 2,
      }),
    );
    renderPage();
    await screen.findByText("Alpha Retail");

    const logo = document.querySelector('img[src="https://cdn.example.com/alpha.png"]');
    expect(logo).toBeInTheDocument();
    // The URL is an ETL snapshot that can rot, so a company without one still gets a mark.
    expect(screen.getByText("B")).toBeInTheDocument();
  });

  it("shows which scope bucket each company matched through", async () => {
    vi.mocked(sourcingApi.getSourcingCompanies).mockResolvedValue(page());
    renderPage();

    await screen.findByText("Alpha Retail");
    // The tier is why a company is in the list at all, so it survived the card view's removal.
    expect(screen.getByText("Direct")).toBeInTheDocument();
    expect(screen.getByText("Adjacent")).toBeInTheDocument();
  });

  it("carries the full value on the cell, so the two-line clamp never hides it outright", async () => {
    const longName =
      "Meridian Energy Group International Holdings Limited Partnership";
    vi.mocked(sourcingApi.getSourcingCompanies).mockResolvedValue(
      page({ companies: [company({ name: longName })], totalCount: 1 }),
    );
    renderPage();

    const cell = await screen.findByText(longName);
    expect(cell.closest("td")).toHaveAttribute("title", longName);
  });

  it("holds the list behind the loader while a Strategy save is in flight, then loads once it settles", async () => {
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    vi.mocked(sourcingApi.getSourcingCompanies).mockResolvedValue(page());

    // Simulate an in-flight Strategy scope save on the shared client (the real one is StrategyPage's autosave).
    let resolveSave!: () => void;
    const savePromise = new Promise<void>((resolve) => (resolveSave = resolve));
    const save = new MutationObserver(client, {
      mutationKey: ["strategy-write", "p1"],
      mutationFn: () => savePromise,
    });
    void save.mutate();

    renderPage(client);

    // While saving: the loader is shown and the list query never fires against the not-yet-written scope.
    expect(screen.queryByText("Alpha Retail")).not.toBeInTheDocument();
    expect(sourcingApi.getSourcingCompanies).not.toHaveBeenCalled();

    // The save settles → the list fetches and renders.
    resolveSave();
    expect(await screen.findByText("Alpha Retail")).toBeInTheDocument();
  });

  it("stops paging while a Strategy save is settling, instead of walking the pre-edit scope", async () => {
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    // More companies than one page holds, so the accumulated list has a next page to reach for.
    vi.mocked(sourcingApi.getSourcingCompanies).mockResolvedValue(page({ totalCount: 100 }));
    renderPage(client);
    await screen.findByText("Alpha Retail");

    const save = new MutationObserver(client, {
      mutationKey: ["strategy-write", "p1"],
      mutationFn: () => new Promise<void>(() => {}),
    });
    void save.mutate();
    await waitFor(() => expect(screen.queryByText("Alpha Retail")).not.toBeInTheDocument());

    // The skeleton rows leave the sentinel in view, and fetchNextPage ignores `enabled` — page 1 of
    // the scope being replaced would land as a success and clear the pending invalidation.
    triggerSentinelIntersect();
    await waitFor(() => expect(sourcingApi.getSourcingCompanies).toHaveBeenCalled());
    expect(sourcingApi.getSourcingCompanies).not.toHaveBeenCalledWith(
      "p1",
      1,
      25,
      "",
      null,
      expect.any(AbortSignal),
    );
  });

  it("aborts a read that a criteria change supersedes, rather than leaving it to run out", async () => {
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    vi.mocked(sourcingApi.getSourcingCompanies).mockReturnValue(new Promise<SourcingResponse>(() => {}));
    renderPage(client);

    await waitFor(() => expect(sourcingApi.getSourcingCompanies).toHaveBeenCalled());
    const signal = vi.mocked(sourcingApi.getSourcingCompanies).mock.calls[0][5];
    expect(signal?.aborted).toBe(false);

    // The count and search behind this run over the whole company universe; a read nobody will read
    // should stop at the server, not just be dropped on arrival.
    await client.cancelQueries({ queryKey: ["sourcing", "p1"] });
    expect(signal?.aborted).toBe(true);
  });

  it("replaces the stale rows with placeholders while refetching after a criteria change", async () => {
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    vi.mocked(sourcingApi.getSourcingCompanies).mockResolvedValue(page());
    renderPage(client);
    await screen.findByText("Alpha Retail");

    // Hold the invalidation-driven refetch open so the in-flight state is observable.
    let resolveRefetch!: (value: SourcingResponse) => void;
    vi.mocked(sourcingApi.getSourcingCompanies).mockReturnValueOnce(
      new Promise<SourcingResponse>((resolve) => {
        resolveRefetch = resolve;
      }),
    );

    void client.invalidateQueries({ queryKey: ["sourcing", "p1"] });

    // The stale companies are hidden, not left flickering on screen — but the header row they sat
    // under stays put, so the table doesn't collapse and jump.
    await waitFor(() =>
      expect(screen.queryByText("Alpha Retail")).not.toBeInTheDocument(),
    );
    expect(
      screen.getByRole("columnheader", { name: /Company/ }),
    ).toBeInTheDocument();

    resolveRefetch(
      page({
        companies: [company({ id: 9, name: "Nova Retail" })],
        totalCount: 1,
      }),
    );
    expect(await screen.findByText("Nova Retail")).toBeInTheDocument();
  });

  it("shows the empty state when nothing matches the scope", async () => {
    vi.mocked(sourcingApi.getSourcingCompanies).mockResolvedValue(
      page({ companies: [], totalCount: 0 }),
    );
    renderPage();

    expect(
      await screen.findByText("No companies match yet"),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "Go to Strategy" }),
    ).toHaveAttribute("href", "/projects/p1/strategy");
  });

  it("fetches the next page when the scroll sentinel comes into view, and appends the results", async () => {
    vi.mocked(sourcingApi.getSourcingCompanies)
      .mockResolvedValueOnce(page({ totalCount: 30 }))
      .mockResolvedValueOnce(
        page({
          page: 1,
          totalCount: 30,
          companies: [
            company({ id: 3, name: "Charlie Retail", location: "Doha, Qatar" }),
          ],
        }),
      );
    renderPage();

    await screen.findByText("Alpha Retail");
    triggerSentinelIntersect();

    await waitFor(() =>
      expect(sourcingApi.getSourcingCompanies).toHaveBeenCalledWith(
        "p1",
        1,
        25,
        "",
        null,
        expect.any(AbortSignal),
      ),
    );
    expect(await screen.findByText("Charlie Retail")).toBeInTheDocument();
    // The first page's companies stay put — this is accumulation, not replacement.
    expect(screen.getByText("Alpha Retail")).toBeInTheDocument();
  });

  it("shows a spinner while the next page is in flight, and clears it once the rows land", async () => {
    let resolveNextPage!: (value: SourcingResponse) => void;
    vi.mocked(sourcingApi.getSourcingCompanies)
      .mockResolvedValueOnce(page({ totalCount: 30 }))
      .mockReturnValueOnce(
        new Promise<SourcingResponse>((resolve) => {
          resolveNextPage = resolve;
        }),
      );
    renderPage();

    await screen.findByText("Alpha Retail");
    triggerSentinelIntersect();

    // Without this the bottom of a 4,000-row list gives no sign that anything is happening.
    expect(await screen.findByRole("status")).toBeInTheDocument();

    resolveNextPage(
      page({
        page: 1,
        totalCount: 30,
        companies: [company({ id: 3, name: "Charlie Retail" })],
      }),
    );
    expect(await screen.findByText("Charlie Retail")).toBeInTheDocument();
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });

  it("does not fetch another page once every company has been loaded", async () => {
    vi.mocked(sourcingApi.getSourcingCompanies).mockResolvedValue(page());
    renderPage();

    await screen.findByText("Alpha Retail");
    triggerSentinelIntersect();

    // totalCount (2) already equals the first page's size — no next page to request. Assert on the
    // spinner rather than the call count: a count assertion inside waitFor passes on its first
    // synchronous run, before any fetch this click started could possibly have been recorded.
    await waitFor(() =>
      expect(screen.queryByRole("status")).not.toBeInTheDocument(),
    );
    expect(sourcingApi.getSourcingCompanies).toHaveBeenCalledTimes(1);
    expect(sourcingApi.getSourcingCompanies).not.toHaveBeenCalledWith(
      "p1",
      1,
      25,
      "",
      null,
    );
  });

  it("links back to Strategy to edit the criteria", async () => {
    vi.mocked(sourcingApi.getSourcingCompanies).mockResolvedValue(page());
    renderPage();

    expect(
      await screen.findByRole("link", { name: /Edit criteria in Strategy/ }),
    ).toHaveAttribute("href", "/projects/p1/strategy");
  });

  describe("sorting by column", () => {
    it("cycles a header through ascending, descending, and back to the default order", async () => {
      vi.mocked(sourcingApi.getSourcingCompanies).mockResolvedValue(page());
      renderPage();
      await screen.findByText("Alpha Retail");

      await userEvent.click(screen.getByRole("button", { name: /Revenue/ }));
      await waitFor(() =>
        expect(sourcingApi.getSourcingCompanies).toHaveBeenCalledWith(
          "p1",
          0,
          25,
          "",
          {
            field: "revenue",
            direction: "asc",
          },
          expect.any(AbortSignal),
        ),
      );
      expect(
        screen.getByRole("columnheader", { name: /Revenue/ }),
      ).toHaveAttribute("aria-sort", "ascending");

      await userEvent.click(screen.getByRole("button", { name: /Revenue/ }));
      await waitFor(() =>
        expect(sourcingApi.getSourcingCompanies).toHaveBeenCalledWith(
          "p1",
          0,
          25,
          "",
          {
            field: "revenue",
            direction: "desc",
          },
          expect.any(AbortSignal),
        ),
      );
      expect(
        screen.getByRole("columnheader", { name: /Revenue/ }),
      ).toHaveAttribute("aria-sort", "descending");

      // A third click drops the sort entirely rather than cycling back to ascending.
      await userEvent.click(screen.getByRole("button", { name: /Revenue/ }));
      await waitFor(() =>
        expect(sourcingApi.getSourcingCompanies).toHaveBeenLastCalledWith(
          "p1",
          0,
          25,
          "",
          null,
          expect.any(AbortSignal),
        ),
      );
      expect(
        screen.getByRole("columnheader", { name: /Revenue/ }),
      ).toHaveAttribute("aria-sort", "none");
    });

    it("starts a newly clicked column at ascending rather than inheriting the last direction", async () => {
      vi.mocked(sourcingApi.getSourcingCompanies).mockResolvedValue(page());
      renderPage();
      await screen.findByText("Alpha Retail");

      await userEvent.click(screen.getByRole("button", { name: /Revenue/ }));
      await userEvent.click(screen.getByRole("button", { name: /Revenue/ }));
      await userEvent.click(screen.getByRole("button", { name: /Company/ }));

      await waitFor(() =>
        expect(sourcingApi.getSourcingCompanies).toHaveBeenLastCalledWith(
          "p1",
          0,
          25,
          "",
          {
            field: "name",
            direction: "asc",
          },
          expect.any(AbortSignal),
        ),
      );
      expect(
        screen.getByRole("columnheader", { name: /Revenue/ }),
      ).toHaveAttribute("aria-sort", "none");
    });

    it("keeps the header row mounted through the refetch its own click triggers", async () => {
      const client = new QueryClient({
        defaultOptions: { queries: { retry: false } },
      });
      vi.mocked(sourcingApi.getSourcingCompanies).mockResolvedValue(page());
      renderPage(client);
      await screen.findByText("Alpha Retail");

      let resolveSorted!: (value: SourcingResponse) => void;
      vi.mocked(sourcingApi.getSourcingCompanies).mockReturnValueOnce(
        new Promise<SourcingResponse>((resolve) => {
          resolveSorted = resolve;
        }),
      );

      await userEvent.click(screen.getByRole("button", { name: /Revenue/ }));

      // Unmounting the header mid-sort would pull the just-clicked button out from under the cursor
      // and drop focus — the same reason the toolbar survives a filter change.
      await waitFor(() =>
        expect(screen.queryByText("Alpha Retail")).not.toBeInTheDocument(),
      );
      expect(
        screen.getByRole("columnheader", { name: /Revenue/ }),
      ).toHaveAttribute("aria-sort", "ascending");

      resolveSorted(page());
      expect(await screen.findByText("Alpha Retail")).toBeInTheDocument();
    });

    it("sorts by match tier, strongest first", async () => {
      vi.mocked(sourcingApi.getSourcingCompanies).mockResolvedValue(page());
      renderPage();
      await screen.findByText("Alpha Retail");

      // Ascending is Direct, then Adjacent, then AI Inferred — the server orders the tier that way.
      await userEvent.click(screen.getByRole("button", { name: /Tier/ }));
      await waitFor(() =>
        expect(sourcingApi.getSourcingCompanies).toHaveBeenLastCalledWith(
          "p1",
          0,
          25,
          "",
          {
            field: "tier",
            direction: "asc",
          },
          expect.any(AbortSignal),
        ),
      );
      expect(
        screen.getByRole("columnheader", { name: /Tier/ }),
      ).toHaveAttribute("aria-sort", "ascending");
    });

    it("offers no sort on a column the backend cannot order by", async () => {
      vi.mocked(sourcingApi.getSourcingCompanies).mockResolvedValue(page());
      renderPage();
      await screen.findByText("Alpha Retail");

      await userEvent.click(screen.getByRole("button", { name: "Columns" }));
      await userEvent.click(columnToggle("Ownership")!);

      // Alphabetising an ownership label answers no question, so it has no sort field server-side.
      expect(
        screen.getByRole("columnheader", { name: "Ownership" }),
      ).toBeInTheDocument();
      expect(
        screen.queryByRole("button", { name: "Ownership" }),
      ).not.toBeInTheDocument();
    });
  });

  describe("choosing columns", () => {
    it("hides a column's header and cells when it is switched off, and restores them", async () => {
      vi.mocked(sourcingApi.getSourcingCompanies).mockResolvedValue(page());
      renderPage();
      await screen.findByText("Alpha Retail");

      await openPicker();
      await userEvent.click(columnToggle("Revenue")!);
      expect(
        screen.queryByRole("columnheader", { name: /Revenue/ }),
      ).not.toBeInTheDocument();
      expect(screen.queryByText("<5M")).not.toBeInTheDocument();

      await userEvent.click(columnToggle("Revenue")!);
      expect(
        screen.getByRole("columnheader", { name: /Revenue/ }),
      ).toBeInTheDocument();
      expect(screen.getByText("<5M")).toBeInTheDocument();
    });

    it("adds a column that starts hidden", async () => {
      vi.mocked(sourcingApi.getSourcingCompanies).mockResolvedValue(page());
      renderPage();
      await screen.findByText("Alpha Retail");

      expect(
        screen.queryByRole("columnheader", { name: /Founded/ }),
      ).not.toBeInTheDocument();

      await openPicker();
      await userEvent.click(columnToggle("Founded")!);
      expect(
        screen.getByRole("columnheader", { name: /Founded/ }),
      ).toBeInTheDocument();
      expect(screen.getAllByText("1998").length).toBeGreaterThan(0);
    });

    it("does not offer to hide Company — a table of attributes belonging to nobody", async () => {
      vi.mocked(sourcingApi.getSourcingCompanies).mockResolvedValue(page());
      renderPage();
      await screen.findByText("Alpha Retail");

      await openPicker();
      expect(columnToggle("Company")).not.toBeInTheDocument();
      expect(
        screen.getByRole("columnheader", { name: /Company/ }),
      ).toBeInTheDocument();
    });

    it("restores the default set", async () => {
      vi.mocked(sourcingApi.getSourcingCompanies).mockResolvedValue(page());
      renderPage();
      await screen.findByText("Alpha Retail");

      await openPicker();
      await userEvent.click(columnToggle("Revenue")!);
      await userEvent.click(columnToggle("Founded")!);
      await userEvent.click(
        screen.getByRole("button", { name: "Reset to default" }),
      );

      expect(
        screen.getByRole("columnheader", { name: /Revenue/ }),
      ).toBeInTheDocument();
      expect(
        screen.queryByRole("columnheader", { name: /Founded/ }),
      ).not.toBeInTheDocument();
    });

    it("remembers the choice per project across a remount", async () => {
      vi.mocked(sourcingApi.getSourcingCompanies).mockResolvedValue(page());
      const first = renderPage();
      await screen.findByText("Alpha Retail");

      await openPicker();
      await userEvent.click(columnToggle("Revenue")!);
      first.unmount();

      renderPage();
      await screen.findByText("Alpha Retail");
      expect(
        screen.queryByRole("columnheader", { name: /Revenue/ }),
      ).not.toBeInTheDocument();
    });

    it("ignores stored entries the table cannot honour", async () => {
      // A column renamed or dropped in a later release must not strand a returning user. Only a
      // stored *boolean* for a column the table still declares is taken; `{...DEFAULT, ...stored}`
      // would instead hand TanStack a non-boolean and hide Revenue on the strength of a truthy string.
      localStorage.setItem(
        "lightmove.sourcing.columns.p1",
        JSON.stringify({ revenue: "nope", someColumnWeDeleted: true }),
      );
      vi.mocked(sourcingApi.getSourcingCompanies).mockResolvedValue(page());
      renderPage();
      await screen.findByText("Alpha Retail");

      expect(
        screen.getByRole("columnheader", { name: /Revenue/ }),
      ).toBeInTheDocument();
    });

    it("honours a stored boolean for a column it still declares", async () => {
      localStorage.setItem(
        "lightmove.sourcing.columns.p1",
        JSON.stringify({ revenue: false }),
      );
      vi.mocked(sourcingApi.getSourcingCompanies).mockResolvedValue(page());
      renderPage();
      await screen.findByText("Alpha Retail");

      expect(
        screen.queryByRole("columnheader", { name: /Revenue/ }),
      ).not.toBeInTheDocument();
    });
  });

  describe("linking out to a company", () => {
    it("shows the links column without anyone switching it on", async () => {
      vi.mocked(sourcingApi.getSourcingCompanies).mockResolvedValue(page());
      renderPage();
      await screen.findByText("Alpha Retail");

      expect(screen.getByRole("columnheader", { name: "Links" })).toBeInTheDocument();
    });

    it("links out to the company's own pages in a new tab", async () => {
      vi.mocked(sourcingApi.getSourcingCompanies).mockResolvedValue(
        page({ companies: [company()], totalCount: 1 }),
      );
      renderPage();
      await screen.findByText("Alpha Retail");

      const website = screen.getByRole("link", {
        name: "Alpha Retail website",
      });
      expect(website).toHaveAttribute("href", "https://alpha.com/");
      expect(website).toHaveAttribute("target", "_blank");
      // Without noopener the opened tab can navigate this one back via window.opener.
      expect(website).toHaveAttribute("rel", "noopener noreferrer");

      expect(
        screen.getByRole("link", { name: "Alpha Retail on LinkedIn" }),
      ).toHaveAttribute("href", "https://linkedin.com/company/alpha");
    });

    it("renders no link for a company the warehouse has none for", async () => {
      vi.mocked(sourcingApi.getSourcingCompanies).mockResolvedValue(
        page({
          companies: [
            company({ website: null, domain: null, linkedinUrl: null }),
          ],
          totalCount: 1,
        }),
      );
      renderPage();
      await screen.findByText("Alpha Retail");

      expect(
        screen.queryByRole("link", { name: /Alpha Retail/ }),
      ).not.toBeInTheDocument();
      expect(screen.getByText("Alpha Retail")).toBeInTheDocument();
    });
  });

  describe("when the columns outgrow the screen", () => {
    it("switches to literal widths and pins Company, in the header's own column order", async () => {
      vi.mocked(sourcingApi.getSourcingCompanies).mockResolvedValue(page());
      const { container } = renderPage();
      await screen.findByText("Alpha Retail");

      // Narrower than the six default columns' minimums add up to.
      reportTableWidth(300);

      await waitFor(() =>
        expect(container.querySelector("col")).toHaveStyle({ width: "290px" }),
      );

      // The colgroup, the header row and the body cells must all be in the same order — pinning
      // reorders columns, and a colgroup built from a different source silently applies each width
      // to the wrong column.
      const widths = [...container.querySelectorAll("col")].map(
        (col) => col.style.width,
      );
      const headers = screen
        .getAllByRole("columnheader")
        .map((header) => header.textContent?.trim());
      expect(widths).toHaveLength(headers.length);
      expect(headers[0]).toMatch(/Company/);
      expect(widths[0]).toBe("290px");
    });

    it("keeps proportional widths while the columns still fit", async () => {
      vi.mocked(sourcingApi.getSourcingCompanies).mockResolvedValue(page());
      const { container } = renderPage();
      await screen.findByText("Alpha Retail");

      reportTableWidth(1400);

      await waitFor(() =>
        expect(container.querySelector("col")?.style.width).toMatch(/%$/),
      );
    });
  });

  describe("filtering by company name", () => {
    it("refetches with the typed name once typing settles", async () => {
      vi.mocked(sourcingApi.getSourcingCompanies).mockResolvedValue(page());
      renderPage();
      await screen.findByText("Alpha Retail");

      await userEvent.type(
        screen.getByLabelText("Filter by company name"),
        "brav",
      );

      await waitFor(() =>
        expect(sourcingApi.getSourcingCompanies).toHaveBeenLastCalledWith(
          "p1",
          0,
          25,
          "brav",
          null,
          expect.any(AbortSignal),
        ),
      );
    });

    it("waits for typing to settle instead of refetching per keystroke", async () => {
      vi.mocked(sourcingApi.getSourcingCompanies).mockResolvedValue(page());
      renderPage();
      await screen.findByText("Alpha Retail");

      await userEvent.type(
        screen.getByLabelText("Filter by company name"),
        "brav",
      );
      await waitFor(() =>
        expect(sourcingApi.getSourcingCompanies).toHaveBeenLastCalledWith(
          "p1",
          0,
          25,
          "brav",
          null,
          expect.any(AbortSignal),
        ),
      );

      // Only the initial unfiltered load and the settled word — none of the three prefixes on the way.
      expect(sourcingApi.getSourcingCompanies).toHaveBeenCalledTimes(2);
      for (const prefix of ["b", "br", "bra"]) {
        expect(sourcingApi.getSourcingCompanies).not.toHaveBeenCalledWith(
          "p1",
          0,
          25,
          prefix,
          null,
          expect.any(AbortSignal),
        );
      }
    });

    it("keeps the filter box focused across the refetch it triggers", async () => {
      vi.mocked(sourcingApi.getSourcingCompanies).mockResolvedValue(page());
      renderPage();
      await screen.findByText("Alpha Retail");

      const box = screen.getByLabelText("Filter by company name");
      await userEvent.type(box, "brav");
      await waitFor(() =>
        expect(sourcingApi.getSourcingCompanies).toHaveBeenCalledTimes(2),
      );

      // The loader replaces the rows, never the toolbar — losing focus mid-word would make the box
      // unusable at anything past one character.
      expect(box).toHaveFocus();
    });

    it("caps the box at the length the server accepts, so an over-long paste can't 400", async () => {
      vi.mocked(sourcingApi.getSourcingCompanies).mockResolvedValue(page());
      renderPage();
      await screen.findByText("Alpha Retail");

      expect(screen.getByLabelText("Filter by company name")).toHaveAttribute(
        "maxLength",
        "100",
      );
    });

    it("narrows the table to the matches and says so in the count", async () => {
      vi.mocked(sourcingApi.getSourcingCompanies).mockImplementation(
        (_id, _page, _size, query) =>
          Promise.resolve(
            query
              ? page({
                  companies: [company({ id: 2, name: "Bravo Retail Group" })],
                  totalCount: 1,
                })
              : page(),
          ),
      );
      renderPage();
      await screen.findByText("Alpha Retail");

      await userEvent.type(
        screen.getByLabelText("Filter by company name"),
        "brav",
      );

      expect(await screen.findByText("Bravo Retail Group")).toBeInTheDocument();
      expect(screen.queryByText("Alpha Retail")).not.toBeInTheDocument();
      // The count is the filtered total, and it says which name it was filtered by.
      expect(screen.getByText("1").closest("div")).toHaveTextContent(
        'companies named "brav" match the current criteria',
      );
    });

    it("offers to clear the filter — not to go to Strategy — when the filter matches nothing", async () => {
      vi.mocked(sourcingApi.getSourcingCompanies).mockImplementation(
        (_id, _page, _size, query) =>
          Promise.resolve(
            query ? page({ companies: [], totalCount: 0 }) : page(),
          ),
      );
      renderPage();
      await screen.findByText("Alpha Retail");

      await userEvent.type(
        screen.getByLabelText("Filter by company name"),
        "zzz",
      );

      expect(
        await screen.findByText('No companies match "zzz"'),
      ).toBeInTheDocument();
      expect(
        screen.queryByRole("link", { name: "Go to Strategy" }),
      ).not.toBeInTheDocument();

      // Two affordances clear it: the box's own ✕ and the empty state's button. This is the latter.
      const clears = screen.getAllByRole("button", {
        name: "Clear name filter",
      });
      await userEvent.click(clears[clears.length - 1]);
      expect(await screen.findByText("Alpha Retail")).toBeInTheDocument();
    });
  });
});
