import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Outlet, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Project } from "../../projects/api/types";
import * as sourcingApi from "../api/sourcingApi";
import type { SourcedCompany, SourcingRun, SourcingRunResponse } from "../api/types";
import { SourcingPage } from "./SourcingPage";

vi.mock("../api/sourcingApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/sourcingApi")>()),
  getCurrentRun: vi.fn(),
  startRun: vi.fn(),
  extendRun: vi.fn(),
}));

/** jsdom has no IntersectionObserver; capture the callback so tests can fire it manually to
 *  simulate the sentinel scrolling into view. */
let observerCallback: IntersectionObserverCallback | null = null;
class IntersectionObserverMock implements IntersectionObserver {
  readonly root = null;
  readonly rootMargin = "";
  readonly scrollMargin = "";
  readonly thresholds: ReadonlyArray<number> = [];
  constructor(callback: IntersectionObserverCallback) {
    observerCallback = callback;
  }
  observe() {}
  unobserve() {}
  disconnect() {}
  takeRecords(): IntersectionObserverEntry[] {
    return [];
  }
}
vi.stubGlobal("IntersectionObserver", IntersectionObserverMock);

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
  companies: 0,
  candidates: 0,
  createdAt: "2026-07-01T00:00:00Z",
};

function company(overrides: Partial<SourcedCompany> = {}): SourcedCompany {
  return {
    coresignalId: 30,
    name: "Rich Retail",
    website: "https://rich.example",
    linkedinUrl: "https://linkedin.com/company/rich-retail",
    logoUrl: "https://logo.example/30.png",
    industry: "Retail",
    sizeRange: "51-200",
    employeesCount: 143,
    revenueRange: "10M-25M",
    revenueAnnualUsd: 18_000_000,
    location: "Dubai, UAE",
    country: "United Arab Emirates",
    foundedYear: 2009,
    description: "Gulf retail group.",
    matchTier: "DIRECT",
    ...overrides,
  };
}

function run(overrides: Partial<SourcingRun> = {}): SourcingRunResponse {
  return {
    run: {
      status: "READY",
      requestedCount: 2,
      collectedCount: 2,
      searchedCount: 2,
      totalMatched: 2,
      criteriaMatchesStrategy: true,
      error: null,
      companies: [
        company(),
        company({ coresignalId: 10, name: "Mid Wholesale", industry: "Wholesale", matchTier: "ADJACENT" }),
      ],
      ...overrides,
    },
  };
}

const renderPage = (client = new QueryClient({ defaultOptions: { queries: { retry: false } } })) =>
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

describe("SourcingPage — the CoreSignal run flow", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it("auto-starts a run when the project has never sourced, then renders its companies", async () => {
    vi.mocked(sourcingApi.getCurrentRun).mockResolvedValue({ run: null });
    vi.mocked(sourcingApi.startRun).mockResolvedValue(run());
    renderPage();

    expect(await screen.findByText("Rich Retail")).toBeInTheDocument();
    expect(sourcingApi.startRun).toHaveBeenCalledWith("p1");
    expect(screen.getByText("Mid Wholesale")).toBeInTheDocument();
    expect(screen.getByText("Direct")).toBeInTheDocument();
    expect(screen.getByText("Adjacent")).toBeInTheDocument();
  });

  it("auto-starts a fresh run when the stored results answer an older strategy", async () => {
    vi.mocked(sourcingApi.getCurrentRun).mockResolvedValue(run({ criteriaMatchesStrategy: false }));
    vi.mocked(sourcingApi.startRun).mockResolvedValue(run());
    renderPage();

    await waitFor(() => expect(sourcingApi.startRun).toHaveBeenCalledWith("p1"));
  });

  it("shows the searching stage while the provider search is in flight", async () => {
    vi.mocked(sourcingApi.getCurrentRun).mockResolvedValue(
      run({ status: "SEARCHING", collectedCount: 0, companies: [] }),
    );
    renderPage();

    expect(await screen.findByText("Searching CoreSignal…")).toBeInTheDocument();
    expect(sourcingApi.startRun).not.toHaveBeenCalled();
  });

  it("streams collecting progress: collected cards plus skeleton slots for the rest", async () => {
    vi.mocked(sourcingApi.getCurrentRun).mockResolvedValue(
      run({ status: "COLLECTING", requestedCount: 3, collectedCount: 1, searchedCount: 3,
            totalMatched: 3, companies: [company()] }),
    );
    renderPage();

    expect(await screen.findByText("Collecting company profiles 1 of 3…")).toBeInTheDocument();
    expect(screen.getByText("Rich Retail")).toBeInTheDocument();
    expect(screen.getAllByTestId("company-skeleton")).toHaveLength(2);
  });

  it("puts website and LinkedIn links on the card, opening in a new tab", async () => {
    vi.mocked(sourcingApi.getCurrentRun).mockResolvedValue(run());
    renderPage();
    await screen.findByText("Rich Retail");

    const website = screen.getByRole("link", { name: "Rich Retail website" });
    expect(website).toHaveAttribute("href", "https://rich.example");
    expect(website).toHaveAttribute("target", "_blank");
    const linkedin = screen.getByRole("link", { name: "Rich Retail LinkedIn" });
    expect(linkedin).toHaveAttribute("href", "https://linkedin.com/company/rich-retail");
  });

  it("opens the detail drawer when a card is clicked, with the full CoreSignal record", async () => {
    vi.mocked(sourcingApi.getCurrentRun).mockResolvedValue(run());
    renderPage();
    await userEvent.click(await screen.findByText("Rich Retail"));

    expect(screen.getByText("Gulf retail group.")).toBeInTheDocument();
    expect(screen.getByText("CoreSignal id")).toBeInTheDocument();
    expect(screen.getByText("Founded")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Website ↗" })).toHaveAttribute(
      "href", "https://rich.example");
  });

  it("shows the failure with its detail and retries through an explicit button only", async () => {
    vi.mocked(sourcingApi.getCurrentRun).mockResolvedValue(
      run({ status: "FAILED", collectedCount: 0, companies: [],
            error: "CoreSignal search failed: out of credits" }),
    );
    vi.mocked(sourcingApi.startRun).mockResolvedValue(run());
    renderPage();

    expect(await screen.findByText("CoreSignal search failed: out of credits")).toBeInTheDocument();
    expect(sourcingApi.startRun).not.toHaveBeenCalled(); // no auto-retry loop on FAILED

    await userEvent.click(screen.getByRole("button", { name: "Retry" }));
    await waitFor(() => expect(sourcingApi.startRun).toHaveBeenCalledWith("p1"));
  });

  it("extends the run when the sentinel scrolls into view and more results exist", async () => {
    vi.mocked(sourcingApi.getCurrentRun).mockResolvedValue(
      run({ requestedCount: 2, searchedCount: 5, totalMatched: 5 }),
    );
    vi.mocked(sourcingApi.extendRun).mockResolvedValue(
      run({ requestedCount: 4, searchedCount: 5, totalMatched: 5 }),
    );
    renderPage();
    await screen.findByText("Rich Retail");

    triggerSentinelIntersect();
    await waitFor(() => expect(sourcingApi.extendRun).toHaveBeenCalledWith("p1"));
  });

  it("does not extend when every searched id has already been requested", async () => {
    vi.mocked(sourcingApi.getCurrentRun).mockResolvedValue(
      run({ requestedCount: 2, searchedCount: 2, totalMatched: 40 }),
    );
    renderPage();
    await screen.findByText("Rich Retail");

    triggerSentinelIntersect();
    expect(sourcingApi.extendRun).not.toHaveBeenCalled();
  });

  it("switches to List view and opens the drawer from a row", async () => {
    vi.mocked(sourcingApi.getCurrentRun).mockResolvedValue(run());
    renderPage();
    await screen.findByText("Rich Retail");

    await userEvent.click(screen.getByTitle("List view"));
    expect(screen.getByRole("columnheader", { name: "Company" })).toBeInTheDocument();
    await userEvent.click(screen.getByText("Mid Wholesale"));
    expect(screen.getByText("CoreSignal id")).toBeInTheDocument();
  });

  it("shows the empty state when a completed run matched nothing", async () => {
    vi.mocked(sourcingApi.getCurrentRun).mockResolvedValue(
      run({ collectedCount: 0, totalMatched: 0, searchedCount: 0, requestedCount: 0, companies: [] }),
    );
    renderPage();

    expect(await screen.findByText("No companies match yet")).toBeInTheDocument();
    expect(screen.getByText("Go to Strategy")).toBeInTheDocument();
  });
});
