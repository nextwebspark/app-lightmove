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
        revenueRange: "<5M", location: "Dubai, UAE" },
      { id: 2, name: "Bravo Retail", domain: "bravo.com", sector: "Retail", employeeRange: "11-50",
        revenueRange: "5M-25M", location: "Riyadh, Saudi Arabia" },
    ],
    totalCount: 2,
    page: 0,
    size: 25,
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

  it("renders the matching companies with their sector and size", async () => {
    vi.mocked(sourcingApi.getSourcingCompanies).mockResolvedValue(page());
    renderPage();

    expect(await screen.findByText("Alpha Retail")).toBeInTheDocument();
    expect(screen.getByText("Bravo Retail")).toBeInTheDocument();
    expect(screen.getAllByText("Retail")).toHaveLength(2);
    expect(screen.getByText("1-10")).toBeInTheDocument();
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

  it("links back to Strategy to edit the criteria", async () => {
    vi.mocked(sourcingApi.getSourcingCompanies).mockResolvedValue(page());
    renderPage();

    expect(await screen.findByRole("link", { name: /Edit criteria in Strategy/ })).toHaveAttribute(
      "href",
      "/projects/p1/strategy",
    );
  });
});
