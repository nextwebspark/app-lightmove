import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Outlet, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import type { Project } from "../../projects/api/types";
import * as companiesApi from "../api/companiesApi";
import * as strategyApi from "../api/strategyApi";
import type { Strategy } from "../api/types";
import { StrategyPage } from "./StrategyPage";

const mockNavigate = vi.fn();
vi.mock("react-router-dom", async (importOriginal) => ({
  ...(await importOriginal<typeof import("react-router-dom")>()),
  useNavigate: () => mockNavigate,
}));
vi.mock("../api/strategyApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/strategyApi")>()),
  getStrategy: vi.fn(),
  putSectors: vi.fn(),
  putCompanySize: vi.fn(),
  putGeography: vi.fn(),
  putOwnership: vi.fn(),
}));
vi.mock("../api/companiesApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/companiesApi")>()),
  getSectors: vi.fn(),
  getSuggestions: vi.fn(),
  getEstimate: vi.fn(),
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

const seeded: Strategy = {
  direct: [{ label: "Retail", selected: true }],
  adjacent: [],
  inferred: [],
  employee: [],
  revenue: [],
  markets: [],
  structures: [],
};

const sectors = {
  sectors: [
    { name: "Retail", count: 1299 },
    { name: "Wholesale", count: 500 },
    { name: "Oil and Gas", count: 1526 },
  ],
};

const renderPage = () =>
  render(
    <MemoryRouter>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
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

describe("StrategyPage — the sector-scope editor", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(strategyApi.getStrategy).mockResolvedValue(seeded);
    vi.mocked(strategyApi.putSectors).mockImplementation((_id, payload) => Promise.resolve(payload));
    vi.mocked(strategyApi.putCompanySize).mockImplementation((_id, employee, revenue) =>
      Promise.resolve({ ...seeded, employee, revenue }),
    );
    vi.mocked(strategyApi.putGeography).mockImplementation((_id, markets) =>
      Promise.resolve({ ...seeded, markets }),
    );
    vi.mocked(strategyApi.putOwnership).mockImplementation((_id, structures) =>
      Promise.resolve({ ...seeded, structures }),
    );
    vi.mocked(companiesApi.getSectors).mockResolvedValue(sectors);
    vi.mocked(companiesApi.getSuggestions).mockResolvedValue({
      adjacent: ["Wholesale"],
      inferredTags: [{ tag: "Grocery Retail", count: 100 }],
    });
    vi.mocked(companiesApi.getEstimate).mockResolvedValue({ count: 4200 });
  });

  it("renders the three groups and the seeded direct sector", async () => {
    renderPage();

    expect(await screen.findByText("Direct")).toBeInTheDocument();
    expect(screen.getByText("Adjacent")).toBeInTheDocument();
    expect(screen.getByText("Inferred")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Retail" })).toHaveAttribute("aria-pressed", "true");
  });

  it("merges arriving suggestions as pre-selected chips", async () => {
    renderPage();

    // The direct sector drives a suggestions fetch; the adjacent tag arrives selected.
    const wholesale = await screen.findByRole("button", { name: "Wholesale" });
    expect(wholesale).toHaveAttribute("aria-pressed", "true");
    expect(await screen.findByRole("button", { name: "Grocery Retail" })).toBeInTheDocument();
    expect(companiesApi.getSuggestions).toHaveBeenCalledWith(["Retail"]);
  });

  it("renders the live estimate for the current scope", async () => {
    renderPage();
    expect(await screen.findByText("4,200")).toBeInTheDocument();
  });

  it("clears the suggestions when the last direct sector is deselected", async () => {
    renderPage();
    // Wholesale is suggested from Retail.
    await screen.findByRole("button", { name: "Wholesale" });

    // Deselect the only direct sector.
    await userEvent.click(screen.getByRole("button", { name: "Retail" }));

    // Its adjacent/inferred suggestions go with it.
    await waitFor(() => expect(screen.queryByRole("button", { name: "Wholesale" })).not.toBeInTheDocument());
    await waitFor(
      () =>
        expect(
          vi.mocked(strategyApi.putSectors).mock.calls.some(
            ([, payload]) => payload.adjacent.length === 0 && payload.inferred.length === 0,
          ),
        ).toBe(true),
      { timeout: 2000 },
    );
  });

  it("toggles a chip off and autosaves the flipped selection", async () => {
    renderPage();
    const retail = await screen.findByRole("button", { name: "Retail" });

    await userEvent.click(retail);

    await waitFor(
      () =>
        expect(vi.mocked(strategyApi.putSectors).mock.calls.some(([, payload]) =>
          payload.direct.some((chip) => chip.label === "Retail" && !chip.selected),
        )).toBe(true),
      { timeout: 2000 },
    );
  });

  it("removes a sector from Adjacent when it is added as Direct", async () => {
    // Wholesale arrives as an adjacent suggestion; promoting it to Direct must strip it from Adjacent.
    renderPage();
    await screen.findByRole("button", { name: "Wholesale" });

    const field = screen.getByLabelText("Add a sector");
    await userEvent.type(field, "Wholesale");
    await userEvent.type(field, "{Enter}");

    await waitFor(
      () =>
        expect(
          vi.mocked(strategyApi.putSectors).mock.calls.some(
            ([, payload]) =>
              payload.direct.some((chip) => chip.label === "Wholesale") &&
              !payload.adjacent.some((chip) => chip.label === "Wholesale"),
          ),
        ).toBe(true),
      { timeout: 2000 },
    );
  });

  it("adds a direct sector through the typeahead", async () => {
    renderPage();
    await screen.findByText("Direct");

    const field = screen.getByLabelText("Add a sector");
    await userEvent.type(field, "Oil");
    // The single match is active; Enter commits it.
    expect(await screen.findByRole("option")).toBeInTheDocument();
    await userEvent.type(field, "{Enter}");

    await waitFor(
      () =>
        expect(vi.mocked(strategyApi.putSectors).mock.calls.some(([, payload]) =>
          payload.direct.some((chip) => chip.label === "Oil and Gas"),
        )).toBe(true),
      { timeout: 2000 },
    );
  });

  it("switches to Company Size and renders both band axes from the catalog", async () => {
    renderPage();
    await userEvent.click(await screen.findByRole("button", { name: "Company Size" }));

    expect(screen.getByText("Employees")).toBeInTheDocument();
    expect(screen.getByText("Revenue")).toBeInTheDocument();
    // A band pill from each catalog, unselected to start.
    expect(screen.getByRole("button", { name: "51–200" })).toHaveAttribute("aria-pressed", "false");
    expect(screen.getByRole("button", { name: "$5M–25M" })).toHaveAttribute("aria-pressed", "false");
  });

  it("toggles a band on and autosaves the selected values for that axis", async () => {
    renderPage();
    await userEvent.click(await screen.findByRole("button", { name: "Company Size" }));

    await userEvent.click(screen.getByRole("button", { name: "51–200" }));

    await waitFor(
      () =>
        expect(
          vi.mocked(strategyApi.putCompanySize).mock.calls.some(
            ([, employee, revenue]) => employee.includes("51-200") && revenue.length === 0,
          ),
        ).toBe(true),
      { timeout: 2000 },
    );
  });

  it("narrows the live estimate by the selected company-size bands too", async () => {
    renderPage();
    await screen.findByText("4,200");
    await userEvent.click(await screen.findByRole("button", { name: "Company Size" }));

    await userEvent.click(screen.getByRole("button", { name: "51–200" }));

    await waitFor(() =>
      expect(
        vi.mocked(companiesApi.getEstimate).mock.calls.some(
          ([, , employeeBands]) => employeeBands.includes("51-200"),
        ),
      ).toBe(true),
    );
  });

  it("navigates to the project's Sourcing screen from the Go to sourcing button", async () => {
    renderPage();

    await userEvent.click(await screen.findByRole("button", { name: /Go to sourcing/ }));

    expect(mockNavigate).toHaveBeenCalledWith("/projects/p1/sourcing");
  });

  it("switches to Ownership Type and renders the structure catalog by display name", async () => {
    renderPage();
    await userEvent.click(await screen.findByRole("button", { name: "Ownership Type" }));

    expect(screen.getByText("Structures")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Publicly listed" })).toHaveAttribute(
      "aria-pressed",
      "false",
    );
    expect(
      screen.getByRole("button", { name: "Subsidiary of foreign multinational" }),
    ).toBeInTheDocument();
  });

  it("toggles a structure on and autosaves its wire token, not its display name", async () => {
    renderPage();
    await userEvent.click(await screen.findByRole("button", { name: "Ownership Type" }));

    await userEvent.click(screen.getByRole("button", { name: "Publicly listed" }));

    await waitFor(
      () =>
        expect(
          vi.mocked(strategyApi.putOwnership).mock.calls.some(([, structures]) =>
            structures.includes("PUBLICLY_LISTED"),
          ),
        ).toBe(true),
      { timeout: 2000 },
    );
  });

  it("switches to Location and renders the market catalog by display name", async () => {
    renderPage();
    await userEvent.click(await screen.findByRole("button", { name: "Location" }));

    expect(screen.getByText("Markets")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "UAE" })).toHaveAttribute("aria-pressed", "false");
    expect(screen.getByRole("button", { name: "Saudi Arabia" })).toBeInTheDocument();
  });

  it("toggles a market on and autosaves its ISO code, not its display name", async () => {
    renderPage();
    await userEvent.click(await screen.findByRole("button", { name: "Location" }));

    await userEvent.click(screen.getByRole("button", { name: "Saudi Arabia" }));

    await waitFor(
      () =>
        expect(
          vi.mocked(strategyApi.putGeography).mock.calls.some(([, markets]) =>
            markets.includes("SA"),
          ),
        ).toBe(true),
      { timeout: 2000 },
    );
  });
});
