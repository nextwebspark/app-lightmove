import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Outlet, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui/Toast";
import type { Project } from "../../projects/api/types";
import * as triageApi from "../api/triageApi";
import type { TriageCompaniesPage } from "../api/types";
import { TriagePage } from "./TriagePage";

vi.mock("../api/triageApi", async (importOriginal) => ({
  ...(await importOriginal<typeof triageApi>()),
  getTriageCompanies: vi.fn(),
  updateTriageCompany: vi.fn(),
}));

const project = { id: "p1", positionTitle: "CFO" } as Project;

const pageOf = (overrides: Partial<TriageCompaniesPage> = {}): TriageCompaniesPage => ({
  companies: [
    {
      id: "u1",
      apolloAccountId: "a1",
      status: "inUniverse",
      note: null,
      companyName: "ACWA Power",
      industry: "oil & energy",
      companyCountry: "Saudi Arabia",
      companyCity: "Riyadh",
      numEmployees: 3000,
      annualRevenue: null,
      website: null,
      logoUrl: null,
    },
  ],
  totalCount: 1,
  page: 0,
  size: 25,
  counts: { inUniverse: 1, shortlisted: 0, declined: 0 },
  ...overrides,
});

const renderPage = () =>
  render(
    <MemoryRouter>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <ToastProvider>
          <Routes>
            <Route element={<Outlet context={{ project }} />}>
              <Route path="/" element={<TriagePage />} />
            </Route>
          </Routes>
        </ToastProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );

describe("TriagePage — a mandate's triaged universe", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(triageApi.getTriageCompanies).mockResolvedValue(pageOf());
  });

  it("lists what the mandate has taken, with the sub-nav counts", async () => {
    renderPage();

    expect(await screen.findByText("ACWA Power")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /In universe/ })).toHaveTextContent("1");
  });

  it("shows an empty universe rather than the market", async () => {
    vi.mocked(triageApi.getTriageCompanies).mockResolvedValue(
      pageOf({ companies: [], totalCount: 0, counts: { inUniverse: 0, shortlisted: 0, declined: 0 } }),
    );
    renderPage();

    // Discovery moved to Strategy; what is left here starts empty and stays so until someone acts,
    // and the empty copy has to point at Strategy rather than at this screen.
    expect(await screen.findByText(/No companies in the universe yet/i)).toBeInTheDocument();
    expect(screen.getByText(/Filter the market on Strategy/i)).toBeInTheDocument();
  });

  it("renders a refused read as an error, not as an empty universe", async () => {
    vi.mocked(triageApi.getTriageCompanies).mockRejectedValue(new Error("forbidden"));
    renderPage();

    // An empty-stage message on a 403 states as fact a number the caller was not allowed to read.
    expect(await screen.findByText(/could not be loaded/i)).toBeInTheDocument();
    expect(screen.queryByText(/No companies in the universe yet/i)).not.toBeInTheDocument();
  });

  it("switching tab asks the server for that status", async () => {
    renderPage();

    await userEvent.click(await screen.findByRole("button", { name: /Shortlisted/ }));

    await waitFor(() =>
      expect(vi.mocked(triageApi.getTriageCompanies).mock.calls.at(-1)![1]).toBe("shortlisted"),
    );
  });

  it("shortlisting a company moves it", async () => {
    vi.mocked(triageApi.updateTriageCompany).mockResolvedValue(pageOf().companies[0]!);
    renderPage();

    await userEvent.click(await screen.findByRole("button", { name: "Shortlist" }));

    await waitFor(() =>
      expect(triageApi.updateTriageCompany).toHaveBeenCalledWith("p1", "u1", {
        status: "shortlisted",
      }),
    );
  });
});
