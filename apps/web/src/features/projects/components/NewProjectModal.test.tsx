import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import * as clientsApi from "../../clients/api/clientsApi";
import type { Client } from "../../clients/api/types";
import * as companiesApi from "../../strategy/api/companiesApi";
import type { CompanySuggestion } from "../../strategy/api/types";
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

vi.mock("../../clients/api/clientsApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../clients/api/clientsApi")>()),
  createClient: vi.fn(),
}));

// The inline-client step is the registry's own company picker, so this suite reads the universe too.
vi.mock("../../strategy/api/companiesApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../strategy/api/companiesApi")>()),
  searchCompanies: vi.fn(),
}));

const suggestion: CompanySuggestion = {
  apolloAccountId: "apollo-1",
  companyName: "Meridian Energy Group",
  industry: "oil & energy",
  companyCity: "Abu Dhabi",
  companyCountry: "United Arab Emirates",
  website: "https://meridian.ae",
  logoUrl: null,
  numEmployees: 4200,
};

beforeEach(() => {
  vi.mocked(positionApi.listTemplates).mockResolvedValue([]);
  vi.mocked(companiesApi.searchCompanies).mockResolvedValue({ companies: [suggestion] });
});

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
    expect(screen.getByPlaceholderText("Search company database…")).toBeInTheDocument();
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

/**
 * The modal used to send every refusal — its own and the server's — to one banner reading "One or
 * more fields are invalid", which named nothing the user could act on.
 */
describe("NewProjectModal — where a refusal is reported", () => {
  const wrap = (children: ReactNode) => (
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <ToastProvider>{children}</ToastProvider>
    </QueryClientProvider>
  );

  const refusal = (code: string, detail: string, fieldErrors?: Record<string, string>) =>
    new ApiRequestError({ code, detail, status: 400, correlationId: "c1", fieldErrors });

  // The suites above share this mock; a "was never called" assertion here would otherwise be reading
  // their calls.
  beforeEach(() => {
    vi.mocked(projectsApi.createProject).mockReset();
  });

  it("reports a missing title at the Position field", async () => {
    const user = userEvent.setup();
    render(wrap(<NewProjectModal open onClose={vi.fn()} clients={CLIENTS} />));

    await user.click(screen.getByRole("button", { name: "Create project" }));

    expect(screen.getByText("Enter the position title")).toBeInTheDocument();
    expect(projectsApi.createProject).not.toHaveBeenCalled();
  });

  // The client check mirrors @Size(max = 160) on CreateProjectRequest.positionTitle. Without it an
  // over-long title posted, was refused, and came back as the anonymous banner.
  it("refuses a title over 160 characters before it reaches the server", async () => {
    const user = userEvent.setup();
    render(wrap(<NewProjectModal open onClose={vi.fn()} clients={CLIENTS} />));

    const field = screen.getByRole("combobox", { name: "Position" });
    await user.type(field, "C".repeat(161));
    await user.click(screen.getByRole("button", { name: "Create project" }));

    // The guard is `> 160` and @Size(max = 160) is inclusive, so the copy must not send a user who
    // trims to exactly 160 into the case it calls refused.
    expect(
      screen.getByText("That title is too long — keep it to 160 characters or fewer"),
    ).toBeInTheDocument();
    expect(field).toHaveAttribute("aria-invalid", "true");
    expect(projectsApi.createProject).not.toHaveBeenCalled();
  });

  // The error used to be cleared only by the next submit, so a corrected title kept the red border
  // and a message that no longer described it.
  it("clears the title error as soon as the title is edited", async () => {
    const user = userEvent.setup();
    render(wrap(<NewProjectModal open onClose={vi.fn()} clients={CLIENTS} />));

    const field = screen.getByRole("combobox", { name: "Position" });
    await user.click(screen.getByRole("button", { name: "Create project" }));
    expect(screen.getByText("Enter the position title")).toBeInTheDocument();

    await user.type(field, "C");

    expect(screen.queryByText("Enter the position title")).not.toBeInTheDocument();
    expect(field).not.toHaveAttribute("aria-invalid", "true");
  });

  // The inline client is a picked company now, not a typed name: submitting with nothing picked must
  // say so at the picker, and picking must clear it without a second submit.
  it("clears the inline-client error as soon as a company is picked", async () => {
    const user = userEvent.setup();
    render(wrap(<NewProjectModal open onClose={vi.fn()} clients={CLIENTS} />));

    await user.selectOptions(screen.getByRole("combobox", { name: /Client/ }), "__new__");
    await user.click(screen.getByRole("button", { name: "Create project" }));
    expect(
      screen.getByText("Pick the client's company, or add it as a new one"),
    ).toBeInTheDocument();

    await user.type(screen.getByPlaceholderText("Search company database…"), "Meridian");
    await user.click(await screen.findByText("Meridian Energy Group"));

    expect(
      screen.queryByText("Pick the client's company, or add it as a new one"),
    ).not.toBeInTheDocument();
  });

  // The whole point of the shared picker: a mandate opened this way posts an apolloAccountId, so the
  // server resolves the canonical name and domain instead of filing a custom duplicate.
  it("creates the inline client from the universe pick, not from a typed name", async () => {
    vi.mocked(clientsApi.createClient).mockResolvedValue(client("new-1", "Meridian Energy Group"));
    vi.mocked(projectsApi.createProject).mockResolvedValue(created("new-1"));
    const user = userEvent.setup();
    render(wrap(<NewProjectModal open onClose={vi.fn()} clients={CLIENTS} />));

    await user.selectOptions(screen.getByRole("combobox", { name: /Client/ }), "__new__");
    await user.type(screen.getByPlaceholderText("Search company database…"), "Meridian");
    await user.click(await screen.findByText("Meridian Energy Group"));
    await user.type(screen.getByRole("combobox", { name: "Position" }), "CFO");
    await user.click(screen.getByRole("button", { name: "Create project" }));

    await waitFor(() =>
      expect(clientsApi.createClient).toHaveBeenCalledWith(
        expect.objectContaining({ company: { apolloAccountId: "apollo-1" } }),
      ),
    );
    await waitFor(() =>
      expect(projectsApi.createProject).toHaveBeenCalledWith(
        expect.objectContaining({ clientId: "new-1" }),
      ),
    );
  });

  it("lets a title of exactly 160 characters through", async () => {
    vi.mocked(projectsApi.createProject).mockResolvedValue(created("acme"));
    const user = userEvent.setup();
    render(wrap(<NewProjectModal open onClose={vi.fn()} clients={CLIENTS} />));

    const title = "C".repeat(160);
    await user.type(screen.getByRole("combobox", { name: "Position" }), title);
    await user.click(screen.getByRole("button", { name: "Create project" }));

    await waitFor(() =>
      expect(projectsApi.createProject).toHaveBeenCalledWith(
        expect.objectContaining({ positionTitle: title }),
      ),
    );
  });

  it("routes the server's field message to the field it names", async () => {
    vi.mocked(projectsApi.createProject).mockRejectedValue(
      refusal("VALIDATION_FAILED", "One or more fields are invalid", {
        positionTitle: "That title is too long",
      }),
    );
    const user = userEvent.setup();
    render(wrap(<NewProjectModal open onClose={vi.fn()} clients={CLIENTS} />));

    await user.type(screen.getByRole("combobox", { name: "Position" }), "CFO");
    await user.click(screen.getByRole("button", { name: "Create project" }));

    expect(await screen.findByText("That title is too long")).toBeInTheDocument();
    expect(screen.queryByText("One or more fields are invalid")).not.toBeInTheDocument();
  });

  it("keeps a form-level refusal in the banner", async () => {
    vi.mocked(projectsApi.createProject).mockRejectedValue(
      refusal("FORBIDDEN", "You don't have permission to do this."),
    );
    const user = userEvent.setup();
    render(wrap(<NewProjectModal open onClose={vi.fn()} clients={CLIENTS} />));

    await user.type(screen.getByRole("combobox", { name: "Position" }), "CFO");
    await user.click(screen.getByRole("button", { name: "Create project" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "You don't have permission to do this.",
    );
  });
});
