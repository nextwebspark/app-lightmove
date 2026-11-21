import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, useLocation } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import { AuthProvider } from "../../auth/AuthProvider";
import * as authApi from "../../auth/api/authApi";
import * as clientsApi from "../../clients/api/clientsApi";
import * as workspaceApi from "../../workspace/api/workspaceApi";
import * as projectsApi from "../api/projectsApi";
import type { Project } from "../api/types";
import { ProjectsPage } from "./ProjectsPage";
import { FOLLOWING_DATE_TIME } from "../../../test/fixtures/user";

vi.mock("../../auth/api/authApi");
vi.mock("../api/projectsApi", async (importOriginal) => ({
  // Keys are real; only the calls are mocked.
  ...(await importOriginal<typeof import("../api/projectsApi")>()),
  projects: vi.fn(),
}));
vi.mock("../../clients/api/clientsApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../clients/api/clientsApi")>()),
  clients: vi.fn(),
}));
vi.mock("../../tasks/api/tasksApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../tasks/api/tasksApi")>()),
  getMyTasks: async () => ({ tasks: [], counts: { today: 0, overdue: 0, upcoming: 0, assignedByMe: 0 }, hasMore: false }),
}));
vi.mock("../../workspace/api/workspaceApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../workspace/api/workspaceApi")>()),
  members: vi.fn(),
}));

// AuthProvider exchanges the refresh cookie for a token before it will ask who the user is, so a test
// that wants a signed-in user has to hand it one.
vi.mock("../../../lib/apiClient", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../../lib/apiClient")>()),
  restoreSession: vi.fn(),
  setAccessToken: vi.fn(),
}));

const { restoreSession } = await import("../../../lib/apiClient");

/**
 * The pure-client view of the workspace home: the server scopes their project list to the mandates
 * they're attached to, so the page must render that list as-is — without the staff-only queries
 * (registry, roster) or the create affordances a client can't use.
 */
describe("ProjectsPage — pure client", () => {
  const client = {
    id: "u1",
    email: "rep@beta-client.example",
    fullName: "Ext Rep",
    title: null,
    avatarUrl: null,
    avatarEditable: true,
    phone: null,
    linkedinUrl: null,
    emailVerified: true,
    hasPassword: true,
    timezone: "Asia/Dubai",
    dateTime: FOLLOWING_DATE_TIME,
    locale: "en",
    platformActions: [],
    pendingInvitations: [],
    termsUpdateRequired: false,
    termsVersion: "2026-07-01",
  workspaces: [],
    workspace: {
      id: "w1",
      name: "Access Firm",
      slug: "access-firm",
      logoMark: "A",
      mode: "COMPANY" as const,
      emailDomain: "access-firm.com",
      joinedAt: null,
      company: null,
      roles: ["CLIENT" as const],
    },
  };

  const attachedMandate: Project = {
    id: "p1",
    clientId: "c1",
    clientName: "Beta Client",
    clientLogoUrl: null,
    positionTitle: "CFO Search",
    stage: "MAPPING",
    health: "OK",
    targetDate: null,
    projectType: "SEARCH",
    startDate: null,
    deliveryDate: null,
    mappingTargetDate: null,
    team: [],
    representatives: [],
    companies: 0,
    candidates: 0,
    mappedCandidates: 0,
    engagedCandidates: 0,
    mappedCompanies: 0,
    createdAt: "2026-07-13T10:00:00Z",
    archivedAt: null,
    archivedByName: null,
  };

  const renderPage = () =>
    render(
      <MemoryRouter>
        <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
          <AuthProvider>
            <ToastProvider>
              <ProjectsPage />
            </ToastProvider>
          </AuthProvider>
        </QueryClientProvider>
      </MemoryRouter>,
    );

  beforeEach(() => {
    // resetAllMocks strips implementations set in the mock factory, so they are set here or not at all.
    vi.resetAllMocks();
    vi.mocked(restoreSession).mockResolvedValue("token");
    vi.mocked(authApi.me).mockResolvedValue(client);
  });

  it("renders the server-scoped mandate list without the staff queries or the create button", async () => {
    vi.mocked(projectsApi.projects).mockResolvedValue([attachedMandate]);

    renderPage();

    // Twice, not once: the list renders a card stack and a table, and CSS shows one per breakpoint.
    expect(await screen.findAllByText("CFO Search")).not.toHaveLength(0);
    expect(screen.queryByText("New position")).not.toBeInTheDocument();
    // The registry and roster are staff surfaces — a pure client must never request them.
    expect(clientsApi.clients).not.toHaveBeenCalled();
    expect(workspaceApi.members).not.toHaveBeenCalled();
  });

  it("links each row's Open button straight to the project", async () => {
    vi.mocked(projectsApi.projects).mockResolvedValue([attachedMandate]);

    renderPage();

    const openLinks = await screen.findAllByRole("link", { name: "Open CFO Search" });
    expect(openLinks).not.toHaveLength(0);
    for (const link of openLinks) expect(link).toHaveAttribute("href", "/projects/p1");
  });

  it("groups the list under business-unit headers, with the nameless bucket last", async () => {
    vi.mocked(projectsApi.projects).mockResolvedValue([
      { ...attachedMandate, id: "p2", clientId: "", clientName: "", positionTitle: "COO Search" },
      attachedMandate,
    ]);

    renderPage();

    expect(await screen.findByText("Beta Client · 1")).toBeInTheDocument();
    const groups = screen.getAllByRole("rowgroup");
    expect(groups).toHaveLength(2);
    expect(within(groups[0]).getAllByText("CFO Search")).not.toHaveLength(0);
    expect(within(groups[1]).getByText("No business unit · 1")).toBeInTheDocument();
    expect(within(groups[1]).getAllByText("COO Search")).not.toHaveLength(0);
  });

  it("shows the no-projects-shared state, with nothing to create, when no mandate is attached", async () => {
    vi.mocked(projectsApi.projects).mockResolvedValue([]);

    renderPage();

    expect(await screen.findByText("No positions shared with you yet")).toBeInTheDocument();
    expect(screen.queryByText("New position")).not.toBeInTheDocument();
    expect(screen.queryByText("Start your first search")).not.toBeInTheDocument();
  });
});

describe("ProjectsPage — staff, an empty workspace and no tasks card", () => {
  const admin = {
    id: "u1",
    email: "ada@firm.example",
    fullName: "Ada Admin",
    title: null,
    avatarUrl: null,
    avatarEditable: true,
    phone: null,
    linkedinUrl: null,
    emailVerified: true,
    hasPassword: true,
    timezone: "Asia/Dubai",
    dateTime: FOLLOWING_DATE_TIME,
    locale: "en",
    platformActions: [],
    pendingInvitations: [],
    termsUpdateRequired: false,
    termsVersion: "2026-07-01",
    workspaces: [],
    workspace: {
      id: "w1",
      name: "Firm",
      slug: "firm",
      logoMark: "F",
      mode: "COMPANY" as const,
      emailDomain: "firm.example",
      joinedAt: null,
      company: null,
      roles: ["ADMIN" as const],
    },
  };
  const aMandate: Project = {
    id: "p1",
    clientId: "c1",
    clientName: "Beta Client",
    clientLogoUrl: null,
    positionTitle: "CFO Search",
    stage: "MAPPING",
    health: "OK",
    targetDate: null,
    projectType: "SEARCH",
    startDate: null,
    deliveryDate: null,
    mappingTargetDate: null,
    team: [],
    representatives: [],
    companies: 0,
    candidates: 0,
    mappedCandidates: 0,
    engagedCandidates: 0,
    mappedCompanies: 0,
    createdAt: "2026-07-13T10:00:00Z",
    archivedAt: null,
    archivedByName: null,
  };

  const renderPage = () =>
    render(
      <MemoryRouter>
        <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
          <AuthProvider>
            <ToastProvider>
              <ProjectsPage />
            </ToastProvider>
          </AuthProvider>
        </QueryClientProvider>
      </MemoryRouter>,
    );

  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(restoreSession).mockResolvedValue("token");
    vi.mocked(authApi.me).mockResolvedValue(admin);
    vi.mocked(clientsApi.clients).mockResolvedValue([]);
    vi.mocked(workspaceApi.members).mockResolvedValue([]);
  });

  it("leads an empty workspace with the header and the empty state, keeping New position", async () => {
    vi.mocked(projectsApi.projects).mockResolvedValue([]);

    renderPage();

    expect(await screen.findByText("Start your first search")).toBeInTheDocument();
    expect(screen.getByText("Positions")).toBeInTheDocument();
    expect(screen.getByText(/A position is one role you're filling/)).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: /new position/i })).toHaveLength(1);
  });

  it("lists every position the server answered, with no tasks card: tasks live on Home", async () => {
    vi.mocked(projectsApi.projects).mockResolvedValue([aMandate]);

    renderPage();

    expect(await screen.findAllByText("CFO Search")).not.toHaveLength(0);
    expect(screen.queryByRole("region", { name: "My tasks" })).not.toBeInTheDocument();
  });
});

describe("ProjectsPage — first use, no results, and a client with nothing shared", () => {
  const staff = (mode: "AGENCY" | "COMPANY" = "COMPANY", roles: ("ADMIN" | "MEMBER" | "CLIENT")[] = ["MEMBER"]) => ({
    id: "u1",
    email: "mona@firm.example",
    fullName: "Mona Member",
    title: null,
    avatarUrl: null,
    avatarEditable: true,
    phone: null,
    linkedinUrl: null,
    emailVerified: true,
    hasPassword: true,
    timezone: "Asia/Dubai",
    dateTime: FOLLOWING_DATE_TIME,
    locale: "en",
    platformActions: [],
    pendingInvitations: [],
    termsUpdateRequired: false,
    termsVersion: "2026-07-01",
    workspaces: [],
    workspace: {
      id: "w1",
      name: "Meridian Search",
      slug: "meridian",
      logoMark: "M",
      mode,
      emailDomain: "firm.example",
      joinedAt: null,
      company: null,
      roles,
    },
  });
  const seatOf = (memberId: string, userId: string) => ({
    memberId,
    userId,
    fullName: "Someone",
    avatarUrl: null,
    workspaceRoles: ["MEMBER" as const],
    projectRoles: ["LEAD" as const],
  });
  const position = (id: string, title: string, seats: ReturnType<typeof seatOf>[], stage: Project["stage"] = "MAPPING"): Project => ({
    id,
    clientId: "c1",
    clientName: "Beta Client",
    clientLogoUrl: null,
    positionTitle: title,
    stage,
    health: "OK",
    targetDate: null,
    projectType: "SEARCH",
    startDate: null,
    deliveryDate: null,
    mappingTargetDate: null,
    team: seats,
    representatives: [],
    companies: 0,
    candidates: 0,
    mappedCandidates: 0,
    engagedCandidates: 0,
    mappedCompanies: 0,
    createdAt: "2026-07-13T10:00:00Z",
    archivedAt: null,
    archivedByName: null,
  });
  const me = { memberId: "m-me", userId: "u1", fullName: "Mona Member", email: "mona@firm.example", avatarUrl: null, roles: ["MEMBER"], joinedAt: null };

  const renderPage = (entry = "/positions") =>
    render(
      <MemoryRouter initialEntries={[entry]}>
        <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
          <AuthProvider>
            <ToastProvider>
              <ProjectsPage />
            </ToastProvider>
          </AuthProvider>
        </QueryClientProvider>
      </MemoryRouter>,
    );

  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(restoreSession).mockResolvedValue("token");
    vi.mocked(clientsApi.clients).mockResolvedValue([]);
  });

  it("tells a member on no position that a lead will add them, not that the workspace has none", async () => {
    vi.mocked(authApi.me).mockResolvedValue(staff());
    vi.mocked(projectsApi.projects).mockResolvedValue([]);

    renderPage();

    expect(await screen.findByText("You're not on any position yet")).toBeInTheDocument();
    expect(screen.getByText(/When a lead adds you to a position, it appears here/)).toBeInTheDocument();
    expect(screen.queryByText("Start your first search")).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /Browse/ })).not.toBeInTheDocument();
  });

  it("says no match only under a search, and Clear filters brings the list back", async () => {
    vi.mocked(authApi.me).mockResolvedValue(staff());
    vi.mocked(workspaceApi.members).mockResolvedValue([me] as never);
    vi.mocked(projectsApi.projects).mockResolvedValue([position("p1", "CFO Search", [seatOf("m-me", "u1")])]);
    const user = userEvent.setup();

    renderPage();
    await screen.findAllByText("CFO Search");
    await user.click(screen.getByRole("button", { name: "Delivered" }));
    expect(await screen.findByText("No positions match")).toBeInTheDocument();
    expect(screen.getByText(/0 of 1 position/)).toBeInTheDocument();
    await user.type(screen.getByPlaceholderText(/Search business unit or position/), "nothing like it");
    await user.click(screen.getByRole("button", { name: "Clear filters" }));

    expect(await screen.findAllByText("CFO Search")).not.toHaveLength(0);
    expect(screen.getByPlaceholderText(/Search business unit or position/)).toHaveValue("");
    // Back on the default stage: the subtitle stops counting "of".
    expect(screen.getByText(/^1 position ·/)).toBeInTheDocument();
  });

  it("keeps an archived position out of every stage, under an Archived chip of its own", async () => {
    vi.mocked(authApi.me).mockResolvedValue(staff());
    vi.mocked(workspaceApi.members).mockResolvedValue([me] as never);
    vi.mocked(projectsApi.projects).mockResolvedValue([
      position("p1", "CFO Search", [seatOf("m-me", "u1")]),
      {
        ...position("p2", "Old Search", [seatOf("m-me", "u1")]),
        archivedAt: "2026-10-01T09:00:00Z",
        archivedByName: "Mona Member",
      },
    ]);
    const user = userEvent.setup();

    renderPage();
    await screen.findAllByText("CFO Search");
    expect(screen.queryByText("Old Search")).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "All stages" }));
    expect(screen.queryByText("Old Search")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /^Archived/ }));

    expect(await screen.findAllByText("Old Search")).not.toHaveLength(0);
    expect(screen.queryByText("CFO Search")).not.toBeInTheDocument();
  });

  it("reads its stage and search from the address, so a return finds the list as it was left", async () => {
    vi.mocked(authApi.me).mockResolvedValue(staff());
    vi.mocked(workspaceApi.members).mockResolvedValue([me] as never);
    vi.mocked(projectsApi.projects).mockResolvedValue([
      position("p1", "CFO Search", [seatOf("m-me", "u1")]),
      position("p2", "COO Search", [seatOf("m-me", "u1")], "DELIVERED"),
    ]);

    renderPage("/positions?stage=DELIVERED&q=coo");

    expect(await screen.findAllByText("COO Search")).not.toHaveLength(0);
    expect(screen.queryByText("CFO Search")).not.toBeInTheDocument();
    expect(screen.getByPlaceholderText(/Search business unit or position/)).toHaveValue("coo");
  });

  it("filters as you type and writes the search into the address a moment later", async () => {
    vi.mocked(authApi.me).mockResolvedValue(staff());
    vi.mocked(workspaceApi.members).mockResolvedValue([me] as never);
    vi.mocked(projectsApi.projects).mockResolvedValue([
      position("p1", "CFO Search", [seatOf("m-me", "u1")]),
      position("p2", "COO Search", [seatOf("m-me", "u1")]),
    ]);
    const user = userEvent.setup();

    render(
      <MemoryRouter initialEntries={["/positions"]}>
        <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
          <AuthProvider>
            <ToastProvider>
              <ProjectsPage />
              <Address />
            </ToastProvider>
          </AuthProvider>
        </QueryClientProvider>
      </MemoryRouter>,
    );
    await screen.findAllByText("CFO Search");
    await user.type(screen.getByPlaceholderText(/Search business unit or position/), "coo");

    expect(screen.getByPlaceholderText(/Search business unit or position/)).toHaveValue("coo");
    expect(screen.queryByText("CFO Search")).not.toBeInTheDocument();
    await waitFor(() => expect(screen.getByTestId("address")).toHaveTextContent("?q=coo"));
    await user.click(screen.getByRole("button", { name: "Delivered" }));
    await waitFor(() => expect(screen.getByTestId("address")).toHaveTextContent("?q=coo&stage=DELIVERED"));
  });

  it("says when everything of theirs is delivered or closed, and offers every stage", async () => {
    vi.mocked(authApi.me).mockResolvedValue(staff());
    vi.mocked(workspaceApi.members).mockResolvedValue([me] as never);
    vi.mocked(projectsApi.projects).mockResolvedValue([position("p1", "Old Search", [seatOf("m-me", "u1")], "DELIVERED")]);
    const user = userEvent.setup();

    renderPage();
    expect(await screen.findByText("No active positions")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Show all stages" }));

    expect(await screen.findAllByText("Old Search")).not.toHaveLength(0);
  });

  it("names the agency to a client contact with nothing shared yet, and keeps the page header", async () => {
    vi.mocked(authApi.me).mockResolvedValue(staff("AGENCY", ["CLIENT"]));
    vi.mocked(projectsApi.projects).mockResolvedValue([]);

    renderPage();

    expect(
      await screen.findByText("When Meridian Search shares a position with you, it'll appear here."),
    ).toBeInTheDocument();
    expect(screen.getByText("Positions")).toBeInTheDocument();
  });

  it("says your talent team to an in-house client contact", async () => {
    vi.mocked(authApi.me).mockResolvedValue(staff("COMPANY", ["CLIENT"]));
    vi.mocked(projectsApi.projects).mockResolvedValue([]);

    renderPage();

    expect(
      await screen.findByText("When your talent team shares a position with you, it'll appear here."),
    ).toBeInTheDocument();
  });
});

function Address() {
  const { search } = useLocation();
  return <div data-testid="address">{search}</div>;
}
