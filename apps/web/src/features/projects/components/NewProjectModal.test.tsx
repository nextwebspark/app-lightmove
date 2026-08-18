import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import type { Client } from "../../clients/api/types";
import * as projectsApi from "../api/projectsApi";
import type { Project } from "../api/types";
import { NewProjectModal } from "./NewProjectModal";

vi.mock("../api/projectsApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/projectsApi")>()),
  createProject: vi.fn(),
}));

const client = (id: string, name: string): Client => ({
  id,
  name,
  type: "RETAINED",
  sector: null,
  hqCountry: null,
  activeMandates: 0,
  deliveredMandates: 0,
  contacts: [],
  viewers: { active: 0, invited: 0 },
});

const CLIENTS = [client("acme", "Acme Corp"), client("globex", "Globex")];

const created = (clientId: string): Project => ({
  id: "p1",
  clientId,
  clientName: "Acme Corp",
  positionTitle: "CFO",
  stage: "BRIEF",
  health: "OK",
  targetDate: null,
  team: [],
  representatives: [],
  companies: 0,
  candidates: 0,
  createdAt: "2026-01-01T00:00:00Z",
});

/**
 * A mandate started from Acme's drawer must land on Acme. The dropdown used to stay editable there —
 * including "New client…" — so the project could be created against Globex while the drawer behind
 * the modal still read Acme.
 */
describe("NewProjectModal — the client the entrance already decided", () => {
  // One client across a rerender, so the staleness test keeps the same component instance rather than
  // relying on React happening to reconcile two hand-built trees.
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const wrap = (children: ReactNode) => (
    <QueryClientProvider client={queryClient}>
      <ToastProvider>{children}</ToastProvider>
    </QueryClientProvider>
  );
  const modal = (lockedClientId?: string) => (
    <NewProjectModal open onClose={vi.fn()} clients={CLIENTS} lockedClientId={lockedClientId} />
  );

  it("locks the client, and creates the project against it", async () => {
    const user = userEvent.setup();
    vi.mocked(projectsApi.createProject).mockResolvedValue(created("acme"));

    render(wrap(modal("acme")));

    const field = screen.getByRole("combobox");
    expect(field).toBeDisabled();
    expect(field).toHaveValue("acme");
    expect(screen.getByText("Acme Corp")).toBeInTheDocument();
    expect(screen.queryByText("Globex")).not.toBeInTheDocument();
    expect(screen.queryByText(/New client/)).not.toBeInTheDocument();
    // The inline-create path must be unreachable, not merely unlabelled.
    expect(screen.queryByPlaceholderText(/Meridian Energy Group/)).not.toBeInTheDocument();

    await user.type(screen.getByPlaceholderText(/Chief Financial Officer/), "CFO");
    await user.click(screen.getByRole("button", { name: "Create project" }));

    await waitFor(() =>
      expect(projectsApi.createProject).toHaveBeenCalledWith(
        expect.objectContaining({ clientId: "acme", positionTitle: "CFO" }),
      ),
    );
  });

  it("still offers the full list and an inline client on the free-choice entrance", async () => {
    const user = userEvent.setup();

    render(wrap(modal()));

    const field = screen.getByRole("combobox");
    expect(field).toBeEnabled();
    expect(screen.getByRole("option", { name: "Globex" })).toBeInTheDocument();

    await user.selectOptions(field, "__new__");
    expect(screen.getByPlaceholderText(/Meridian Energy Group/)).toBeInTheDocument();
  });

  it("submits the client the prop names now, not the one it named at mount", async () => {
    const user = userEvent.setup();
    vi.mocked(projectsApi.createProject).mockResolvedValue(created("globex"));

    const { rerender } = render(wrap(modal("acme")));
    expect(screen.getByRole("combobox")).toHaveValue("acme");

    rerender(wrap(modal("globex")));

    expect(screen.getByRole("combobox")).toHaveValue("globex");
    expect(screen.getByText("Globex")).toBeInTheDocument();

    await user.type(screen.getByPlaceholderText(/Chief Financial Officer/), "CTO");
    await user.click(screen.getByRole("button", { name: "Create project" }));

    await waitFor(() =>
      expect(projectsApi.createProject).toHaveBeenCalledWith(
        expect.objectContaining({ clientId: "globex" }),
      ),
    );
  });
});
