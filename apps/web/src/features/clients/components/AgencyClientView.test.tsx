import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import { aUser, aWorkspace } from "../../../test/fixtures/user";
import * as companiesApi from "../../strategy/api/companiesApi";
import type { CompanyResult } from "../../strategy/api/types";
import * as clientsApi from "../api/clientsApi";
import type { ClientDetail } from "../api/types";
import { ClientDrawer } from "./ClientDrawer";

vi.mock("../api/clientsApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/clientsApi")>()),
  client: vi.fn(),
  updateClient: vi.fn(),
  updateClientPersona: vi.fn(),
}));

vi.mock("../../strategy/api/companiesApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../strategy/api/companiesApi")>()),
  getCompany: vi.fn(),
  getFacets: vi.fn(),
}));

vi.mock("../../../lib/countries", () => import("../../../test/countries"));

vi.mock("../../auth/AuthProvider", () => ({
  useAuth: () => ({ user: aUser({ workspace: aWorkspace({ mode: "AGENCY" }) }) }),
}));

const typedClient: ClientDetail = {
  id: "c1",
  name: "Harbour Health",
  apolloAccountId: null,
  sector: "Hospitals",
  hqCountry: "Saudi Arabia",
  hqCity: null,
  logoUrl: null,
  domain: "harbourhealth.example",
  offLimitsNote: null,
  notes: null,
  persona: { summary: null, sectors: ["Hospitals"], competitors: [], geographies: ["Saudi Arabia"], notes: null },
  activeMandates: 0,
  deliveredMandates: 0,
  representatives: [],
  mandates: [],
};

const universeClient: ClientDetail = {
  ...typedClient,
  id: "c2",
  name: "Ministry of Health",
  apolloAccountId: "apollo-moh",
  logoUrl: "https://logos.example/moh.png",
};

const ministry = {
  apolloAccountId: "apollo-moh",
  companyName: "Ministry of Health Saudi Arabia",
  industry: "government administration",
  companyCity: "Riyadh",
  companyCountry: "Saudi Arabia",
  numEmployees: 206000,
  annualRevenue: 6_000_000,
  website: "https://moh.gov.sa",
  logoUrl: "https://logos.example/moh.png",
  shortDescription: "The main governmental body responsible for the healthcare system in the Kingdom.",
  foundedYear: 1951,
  companyLinkedinUrl: "https://www.linkedin.com/company/moh-saudi",
} as CompanyResult;

const renderDrawer = (clientId: string) =>
  render(
    <MemoryRouter>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <ToastProvider>
          <ClientDrawer clientId={clientId} onClose={() => {}} onNewMandate={() => {}} />
        </ToastProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );

beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(companiesApi.getFacets).mockRejectedValue(new Error("not needed"));
  vi.mocked(companiesApi.getCompany).mockResolvedValue(ministry);
  vi.mocked(clientsApi.client).mockImplementation(async (id) => (id === "c2" ? universeClient : typedClient));
});

describe("AgencyClientView — a client picked from the company database", () => {
  it("reads as the company panel: the universe's About and Scale snapshot, read only", async () => {
    renderDrawer("c2");

    expect(await screen.findByText(/main governmental body/)).toBeInTheDocument();
    expect(companiesApi.getCompany).toHaveBeenCalledWith("apollo-moh", expect.anything());
    expect(screen.getByText("Scale snapshot")).toBeInTheDocument();
    expect(screen.getByText("206,000")).toBeInTheDocument();
    expect(screen.getByText("1951")).toBeInTheDocument();
    expect(screen.queryByText("Client record")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Sector")).not.toBeInTheDocument();
  });

  it("saves the agency's own name and notes, never the universe's facts", async () => {
    vi.mocked(clientsApi.updateClient).mockResolvedValue(universeClient);
    const user = userEvent.setup();
    renderDrawer("c2");

    await user.type(await screen.findByLabelText("Notes"), "Retained for the C-suite");
    await user.click(screen.getByRole("button", { name: "Save changes" }));

    await waitFor(() =>
      expect(clientsApi.updateClient).toHaveBeenCalledWith("c2", {
        name: "Ministry of Health",
        notes: "Retained for the C-suite",
      }),
    );
  });
});

describe("AgencyClientView — a client typed in by hand", () => {
  it("offers its sector, country and website to edit, and asks the universe nothing", async () => {
    vi.mocked(clientsApi.updateClient).mockResolvedValue(typedClient);
    const user = userEvent.setup();
    renderDrawer("c1");

    const sector = await screen.findByLabelText("Sector");
    await user.clear(sector);
    await user.type(sector, "Health Care");
    await user.click(screen.getByRole("button", { name: "Save changes" }));

    await waitFor(() =>
      expect(clientsApi.updateClient).toHaveBeenCalledWith("c1", {
        name: "Harbour Health",
        notes: "",
        sector: "Health Care",
        hqCountry: "Saudi Arabia",
        domain: "harbourhealth.example",
      }),
    );
    expect(companiesApi.getCompany).not.toHaveBeenCalled();
  });
});

describe("AgencyClientView — the client persona", () => {
  it("saves what the agency records about the client for the assistant", async () => {
    vi.mocked(clientsApi.updateClientPersona).mockImplementation(async (_id, persona) => ({
      ...typedClient,
      persona,
    }));
    const user = userEvent.setup();
    renderDrawer("c1");

    const section = (await screen.findByRole("heading", { name: "Client persona" })).closest("section")!;
    await user.type(within(section).getByPlaceholderText(/private hospital operator/), "Private hospital group");
    await user.click(within(section).getByRole("button", { name: "Save persona" }));

    await waitFor(() =>
      expect(clientsApi.updateClientPersona).toHaveBeenCalledWith("c1", {
        summary: "Private hospital group",
        sectors: ["Hospitals"],
        competitors: [],
        geographies: ["Saudi Arabia"],
        notes: null,
      }),
    );
  });

  it("lists the client's contacts and positions under the agency's words", async () => {
    renderDrawer("c1");

    expect(await screen.findByText("Client contacts")).toBeInTheDocument();
    expect(screen.getByText("No positions yet — open one for this client.")).toBeInTheDocument();
  });
});
