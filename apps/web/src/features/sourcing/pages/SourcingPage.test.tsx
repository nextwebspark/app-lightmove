import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Outlet, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Project } from "../../projects/api/types";
import * as sourcingApi from "../api/sourcingApi";
import type { SourcingResponse } from "../api/types";
import { SourcingPage } from "./SourcingPage";

vi.mock("../api/sourcingApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/sourcingApi")>()),
  getSourcingCompanies: vi.fn(),
}));

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

function page(overrides: Partial<SourcingResponse> = {}): SourcingResponse {
  return {
    companies: [
      { id: 1, name: "Alpha Retail", domain: "alpha.com", sector: "Retail", employeeRange: "1-10",
        revenueRange: "<5M", location: "Dubai, UAE", matchTier: "DIRECT" },
      { id: 2, name: "Bravo Retail", domain: "bravo.com", sector: "Wholesale", employeeRange: "11-50",
        revenueRange: "5M-25M", location: "Riyadh, Saudi Arabia", matchTier: "ADJACENT" },
    ],
    totalCount: 2,
    page: 0,
    size: 25,
    appliedFilters: { sector: true, employee: true, revenue: true },
    ...overrides,
  };
}

const renderPage = () =>
  render(
    <MemoryRouter initialEntries={["/"]}>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <Routes>
          <Route element={<Outlet context={{ project }} />}>
            <Route path="/" element={<SourcingPage />} />
          </Route>
        </Routes>
      </QueryClientProvider>
    </MemoryRouter>,
  );

describe("SourcingPage — the filtered company list", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it("renders as Card view by default, with each company's meta line", async () => {
    vi.mocked(sourcingApi.getSourcingCompanies).mockResolvedValue(page());
    renderPage();

    expect(await screen.findByText("Alpha Retail")).toBeInTheDocument();
    expect(screen.getByText("Bravo Retail")).toBeInTheDocument();
    expect(screen.getByText("Dubai, UAE · Retail")).toBeInTheDocument();
    // The List-only column headers are not shown until the user switches views.
    expect(screen.queryByRole("columnheader", { name: "Company" })).not.toBeInTheDocument();
  });

  it("switches to List view and back to Card view", async () => {
    vi.mocked(sourcingApi.getSourcingCompanies).mockResolvedValue(page());
    renderPage();
    await screen.findByText("Alpha Retail");

    await userEvent.click(screen.getByRole("button", { name: "List view" }));
    expect(screen.getByRole("columnheader", { name: "Company" })).toBeInTheDocument();
    expect(screen.getByText("1-10")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Card view" }));
    expect(screen.queryByRole("columnheader", { name: "Company" })).not.toBeInTheDocument();
    expect(screen.getByText("Dubai, UAE · Retail")).toBeInTheDocument();
  });

  it("shows the empty state when nothing matches the scope", async () => {
    vi.mocked(sourcingApi.getSourcingCompanies).mockResolvedValue(
      page({ companies: [], totalCount: 0 }),
    );
    renderPage();

    expect(await screen.findByText("No companies match yet")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Go to Strategy" })).toHaveAttribute(
      "href",
      "/projects/p1/strategy",
    );
  });

  it("pages forward and requests the next page from the API", async () => {
    vi.mocked(sourcingApi.getSourcingCompanies).mockResolvedValue(page({ totalCount: 30 }));
    renderPage();

    await screen.findByText("Alpha Retail");
    await userEvent.click(screen.getByRole("button", { name: "Next" }));

    await waitFor(() =>
      expect(sourcingApi.getSourcingCompanies).toHaveBeenCalledWith("p1", 1, 25),
    );
  });

  it("shows which scope bucket each card matched through", async () => {
    vi.mocked(sourcingApi.getSourcingCompanies).mockResolvedValue(page());
    renderPage();

    await screen.findByText("Alpha Retail");
    expect(screen.getByText("Direct")).toBeInTheDocument();
    expect(screen.getByText("Adjacent")).toBeInTheDocument();
  });

  it("shows a checkmarked Criteria Met row for each applied filter, with the matched value", async () => {
    vi.mocked(sourcingApi.getSourcingCompanies).mockResolvedValue(
      page({ companies: [page().companies[0]] }),
    );
    renderPage();

    await screen.findByText("Alpha Retail");
    expect(screen.getByText("3 of 3 met")).toBeInTheDocument();
    // Each matched value appears twice: once in the checkmarked Criteria Met row, once in Scale Snapshot.
    expect(screen.getAllByText("<5M")).toHaveLength(2);
    expect(screen.getAllByText("1-10")).toHaveLength(2);
  });

  it("omits a Criteria Met row for a filter that wasn't applied", async () => {
    vi.mocked(sourcingApi.getSourcingCompanies).mockResolvedValue(
      page({
        companies: [page().companies[0]],
        appliedFilters: { sector: true, employee: true, revenue: false },
      }),
    );
    renderPage();

    await screen.findByText("Alpha Retail");
    expect(screen.getByText("2 of 2 met")).toBeInTheDocument();
    // Revenue's own Criteria Met row is gone, but Scale Snapshot's Revenue row (unaffected) still shows.
    expect(screen.getAllByText("Revenue")).toHaveLength(1);
  });

  it("shows the full Scale Snapshot — Revenue, Employees, Region, and Sector", async () => {
    vi.mocked(sourcingApi.getSourcingCompanies).mockResolvedValue(page({ companies: [page().companies[0]] }));
    renderPage();

    await screen.findByText("Alpha Retail");
    // Revenue/Employees/Sector labels also appear in the Criteria Met column (all applied here);
    // Region is Scale-Snapshot-only, since geography scoping isn't built yet.
    expect(screen.getAllByText("Revenue").length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText("Employees").length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText("Region")).toBeInTheDocument();
    expect(screen.getAllByText("Sector").length).toBeGreaterThanOrEqual(1);
  });

  it("renders the placeholder triage buttons as disabled", async () => {
    vi.mocked(sourcingApi.getSourcingCompanies).mockResolvedValue(page({ companies: [page().companies[0]] }));
    renderPage();

    await screen.findByText("Alpha Retail");
    expect(screen.getByRole("button", { name: "Comment" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Add to universe" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Shortlist" })).toBeDisabled();
  });

  it("links back to Strategy to edit the criteria", async () => {
    vi.mocked(sourcingApi.getSourcingCompanies).mockResolvedValue(page());
    renderPage();

    expect(await screen.findByRole("link", { name: /Edit criteria in Strategy/ })).toHaveAttribute(
      "href",
      "/projects/p1/strategy",
    );
  });
});
