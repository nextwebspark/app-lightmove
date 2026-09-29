import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import { aUser, aWorkspace } from "../../../test/fixtures/user";
import type { WorkspaceMode } from "../../auth/api/types";
import * as companiesApi from "../../strategy/api/companiesApi";
import * as clientsApi from "../api/clientsApi";
import type { Client } from "../api/types";
import { NewClientModal } from "./NewClientModal";

const session = vi.hoisted(() => ({ mode: "COMPANY" as WorkspaceMode }));

vi.mock("../../auth/AuthProvider", () => ({
  useAuth: () => ({ user: aUser({ workspace: aWorkspace({ mode: session.mode }) }) }),
}));

vi.mock("../../../lib/countries", () => import("../../../test/countries"));

vi.mock("../../strategy/api/companiesApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../strategy/api/companiesApi")>()),
  searchCompanies: vi.fn(),
}));

vi.mock("../api/clientsApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/clientsApi")>()),
  createClient: vi.fn(),
}));

const client = (id: string, name: string): Client => ({
  id,
  name,
  type: "RETAINED",
  sector: null,
  hqCountry: null,
  hqCity: null,
  logoUrl: null,
  activeMandates: 0,
  deliveredMandates: 0,
  contacts: [],
  viewers: { active: 0, invited: 0 },
});

const CLIENTS = [client("finance", "Group Finance")];

const renderModal = () =>
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <ToastProvider>
        <NewClientModal
          open
          onClose={vi.fn()}
          clients={CLIENTS}
          existingNames={new Set(CLIENTS.map((unit) => unit.name.toLowerCase()))}
          onCreated={vi.fn()}
        />
      </ToastProvider>
    </QueryClientProvider>,
  );

beforeEach(() => {
  vi.mocked(clientsApi.createClient).mockReset();
  vi.mocked(companiesApi.searchCompanies).mockReset();
  vi.mocked(companiesApi.searchCompanies).mockResolvedValue({ companies: [] });
});

/** A business unit is a department of the firm, so the company database has nothing to offer it. */
describe("NewClientModal — an in-house business unit", () => {
  beforeEach(() => {
    session.mode = "COMPANY";
  });

  it("names the unit without searching the company database", async () => {
    vi.mocked(clientsApi.createClient).mockResolvedValue(client("new-1", "Data & Analytics"));
    const user = userEvent.setup();
    renderModal();

    expect(screen.queryByPlaceholderText("Search company database…")).not.toBeInTheDocument();
    await user.type(screen.getByRole("combobox", { name: /Business unit/ }), " Data & Analytics ");
    await user.click(screen.getByRole("button", { name: "Create business unit" }));

    await waitFor(() =>
      expect(clientsApi.createClient).toHaveBeenCalledWith({
        customName: "Data & Analytics",
        primaryContact: null,
      }),
    );
    expect(companiesApi.searchCompanies).not.toHaveBeenCalled();
  });

  it("refuses a name the organisation already holds before anything is posted", async () => {
    const user = userEvent.setup();
    renderModal();

    await user.type(screen.getByRole("combobox", { name: /Business unit/ }), "group finance");

    expect(screen.getByText("group finance is already a business unit")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Create business unit" })).not.toBeInTheDocument();
  });
});

describe("NewClientModal — an agency's client", () => {
  beforeEach(() => {
    session.mode = "AGENCY";
  });

  it("searches the company database", async () => {
    const user = userEvent.setup();
    renderModal();

    await user.type(screen.getByPlaceholderText("Search company database…"), "meri");

    await waitFor(() =>
      expect(companiesApi.searchCompanies).toHaveBeenCalledWith("meri", undefined, expect.anything()),
    );
  });
});
