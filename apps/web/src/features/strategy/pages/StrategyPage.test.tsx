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

vi.mock("../api/strategyApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/strategyApi")>()),
  getStrategy: vi.fn(),
  putSectors: vi.fn(),
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
});
