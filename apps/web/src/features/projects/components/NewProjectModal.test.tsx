import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import type { Client } from "../../clients/api/types";
import * as positionApi from "../../position/api/positionApi";
import type { PositionTemplate } from "../../position/api/types";
import * as projectsApi from "../api/projectsApi";
import type { Project } from "../api/types";
import { NewProjectModal } from "./NewProjectModal";

vi.mock("../api/projectsApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/projectsApi")>()),
  createProject: vi.fn(),
}));

vi.mock("../../position/api/positionApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../position/api/positionApi")>()),
  listTemplates: vi.fn(),
}));

beforeEach(() => {
  vi.mocked(positionApi.listTemplates).mockResolvedValue([]);
});

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

    const field = screen.getByRole("combobox", { name: /Client/ });
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

    const field = screen.getByRole("combobox", { name: /Client/ });
    expect(field).toBeEnabled();
    expect(screen.getByRole("option", { name: "Globex" })).toBeInTheDocument();

    await user.selectOptions(field, "__new__");
    expect(screen.getByPlaceholderText(/Meridian Energy Group/)).toBeInTheDocument();
  });

  it("submits the client the prop names now, not the one it named at mount", async () => {
    const user = userEvent.setup();
    vi.mocked(projectsApi.createProject).mockResolvedValue(created("globex"));

    const { rerender } = render(wrap(modal("acme")));
    expect(screen.getByRole("combobox", { name: /Client/ })).toHaveValue("acme");

    rerender(wrap(modal("globex")));

    expect(screen.getByRole("combobox", { name: /Client/ })).toHaveValue("globex");
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

const template = (id: string, code: string, title: string): PositionTemplate => ({
  id,
  code,
  title,
  discipline: "FINANCE",
  seniority: "C_SUITE",
  summary: null,
  shared: true,
});

const TEMPLATES = [
  template("t-cfo", "cfo", "Chief Financial Officer"),
  template("t-cco", "cco", "Chief Compliance Officer"),
  template("t-hoc", "head-of-compliance", "Head of Compliance"),
];

/**
 * The Position field is the same combobox as the brief's step one: templates offered on focus and
 * filtered while typing, free text always allowed. Picking only fills the title — the server seeds
 * the brief from it at creation, so no template id travels with the form.
 */
describe("NewProjectModal — the role-template picker on the Position field", () => {
  // A fresh cache per render: the catalog query is stale-timed, and one test's catalog (or failure)
  // must not answer the next test's field.
  const wrap = (children: ReactNode) => (
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <ToastProvider>{children}</ToastProvider>
    </QueryClientProvider>
  );

  it("offers the catalog on focus, filters it while typing, and commits nothing on Enter", async () => {
    vi.mocked(positionApi.listTemplates).mockResolvedValue(TEMPLATES);
    const user = userEvent.setup();

    render(wrap(<NewProjectModal open onClose={vi.fn()} clients={CLIENTS} />));

    const field = screen.getByRole("combobox", { name: "Position" });
    await user.click(field);
    expect((await screen.findByRole("listbox")).children).toHaveLength(3);

    await user.type(field, "complian");
    const options = within(screen.getByRole("listbox")).getAllByRole("option");
    expect(options.map((option) => option.textContent)).toEqual([
      expect.stringContaining("Chief Compliance Officer"),
      expect.stringContaining("Head of Compliance"),
    ]);

    // The field is the value and the list is an offer: Enter must not commit the row under it.
    await user.type(field, "{Enter}");
    expect(field).toHaveValue("complian");
  });

  it("fills the title from a picked template, and creates the project with it", async () => {
    vi.mocked(positionApi.listTemplates).mockResolvedValue(TEMPLATES);
    vi.mocked(projectsApi.createProject).mockResolvedValue(created("acme"));
    const user = userEvent.setup();

    render(wrap(<NewProjectModal open onClose={vi.fn()} clients={CLIENTS} />));

    const field = screen.getByRole("combobox", { name: "Position" });
    await user.click(field);
    await user.click(
      within(await screen.findByRole("listbox")).getByRole("option", {
        name: /Chief Financial Officer/,
      }),
    );

    expect(field).toHaveValue("Chief Financial Officer");
    await user.click(screen.getByRole("button", { name: "Create project" }));

    await waitFor(() =>
      expect(projectsApi.createProject).toHaveBeenCalledWith(
        expect.objectContaining({ positionTitle: "Chief Financial Officer" }),
      ),
    );
  });

  it("keeps the field typeable when the catalog cannot be read", async () => {
    vi.mocked(positionApi.listTemplates).mockRejectedValue(new Error("nope"));
    vi.mocked(projectsApi.createProject).mockResolvedValue(created("acme"));
    const user = userEvent.setup();

    render(wrap(<NewProjectModal open onClose={vi.fn()} clients={CLIENTS} />));

    const field = screen.getByRole("combobox", { name: "Position" });
    await user.type(field, "Group CFO – Energy Division");

    expect(field).toHaveValue("Group CFO – Energy Division");
    expect(screen.queryByRole("listbox")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Create project" }));
    await waitFor(() =>
      expect(projectsApi.createProject).toHaveBeenCalledWith(
        expect.objectContaining({ positionTitle: "Group CFO – Energy Division" }),
      ),
    );
  });

  it("closes the list on Escape, and the modal only on the second press", async () => {
    vi.mocked(positionApi.listTemplates).mockResolvedValue(TEMPLATES);
    const onClose = vi.fn();
    const user = userEvent.setup();

    render(wrap(<NewProjectModal open onClose={onClose} clients={CLIENTS} />));

    const field = screen.getByRole("combobox", { name: "Position" });
    await user.click(field);
    await screen.findByRole("listbox");

    await user.keyboard("{Escape}");
    expect(screen.queryByRole("listbox")).not.toBeInTheDocument();
    expect(onClose).not.toHaveBeenCalled();

    await user.keyboard("{Escape}");
    expect(onClose).toHaveBeenCalled();
  });
});
