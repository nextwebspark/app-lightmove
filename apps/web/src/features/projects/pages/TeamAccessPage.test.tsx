import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Outlet, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import { AuthProvider } from "../../auth/AuthProvider";
import * as authApi from "../../auth/api/authApi";
import * as clientsApi from "../../clients/api/clientsApi";
import * as workspaceApi from "../../workspace/api/workspaceApi";
import * as projectsApi from "../api/projectsApi";
import type { Project } from "../api/types";
import { TeamAccessPage } from "./TeamAccessPage";

vi.mock("../../auth/api/authApi");
vi.mock("../api/projectsApi", async (importOriginal) => ({
  // Keys are real; only the calls are mocked.
  ...(await importOriginal<typeof import("../api/projectsApi")>()),
  attachRepresentative: vi.fn(),
  detachRepresentative: vi.fn(),
  inviteRepresentativeToProject: vi.fn(),
  putProjectMember: vi.fn(),
  removeProjectMember: vi.fn(),
}));
vi.mock("../../workspace/api/workspaceApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../workspace/api/workspaceApi")>()),
  members: vi.fn(),
}));
vi.mock("../../clients/api/clientsApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../clients/api/clientsApi")>()),
  client: vi.fn(),
  inviteRepresentative: vi.fn(),
}));
vi.mock("../../../lib/apiClient", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../../lib/apiClient")>()),
  restoreSession: vi.fn(),
  setAccessToken: vi.fn(),
}));

const { restoreSession } = await import("../../../lib/apiClient");

/**
 * The Team & access tab: the staff table with its one-role-per-seat chips, the attached client
 * contacts with their attachment state, the manage affordances behind the lead/workspace-admin gate,
 * and the pure-client rendering that must never touch the staff-only client registry.
 */
describe("TeamAccessPage", () => {
  const admin = {
    id: "u1",
    email: "alok@firm.example",
    fullName: "Alok Kumar",
    title: null,
    avatarUrl: null,
    emailVerified: true,
    hasPassword: true,
    timezone: "Asia/Dubai",
    locale: "en",
    pendingInvitation: null,
    workspace: {
      id: "w1",
      name: "Firm",
      slug: "firm",
      logoMark: "F",
      emailDomain: "firm.example",
      joinedAt: null,
      roles: ["ADMIN" as const],
    },
  };

  const researcher = {
    ...admin,
    id: "u2",
    email: "sara@firm.example",
    fullName: "Sara Al-Mansour",
    workspace: { ...admin.workspace, roles: ["MEMBER" as const] },
  };

  const pureClient = {
    ...admin,
    id: "u9",
    email: "rep@beta-client.example",
    fullName: "Ext Rep",
    workspace: { ...admin.workspace, roles: ["CLIENT" as const] },
  };

  const project: Project = {
    id: "p1",
    clientId: "c1",
    clientName: "Beta Client",
    positionTitle: "CFO Search",
    stage: "MAPPING",
    health: "OK",
    targetDate: null,
    team: [
      {
        memberId: "m1",
        userId: "u1",
        fullName: "Alok Kumar",
        avatarUrl: null,
        workspaceRoles: ["ADMIN"],
        projectRoles: ["LEAD"],
      },
      {
        memberId: "m2",
        userId: "u2",
        fullName: "Sara Al-Mansour",
        avatarUrl: null,
        workspaceRoles: ["MEMBER"],
        projectRoles: ["RESEARCHER"],
      },
      // A pure-client seat: it belongs to the Client section below, never the staff table.
      {
        memberId: "m9",
        userId: "u9",
        fullName: "Ext Rep",
        avatarUrl: null,
        workspaceRoles: ["CLIENT"],
        projectRoles: ["CLIENT"],
      },
    ],
    representatives: [
      {
        representativeId: "r1",
        fullName: "Seated Rep",
        position: "Chair",
        email: "seated@beta-client.example",
        status: "ACTIVE",
      },
      {
        representativeId: "r2",
        fullName: "Pending Rep",
        position: "CHRO",
        email: "pending@beta-client.example",
        status: "INVITED",
      },
    ],
    companies: 0,
    candidates: 0,
    createdAt: "2026-07-13T10:00:00Z",
  };

  // The page reads the project from ProjectLayout's outlet — a bare shell stands in for the layout.
  const renderPage = () =>
    render(
      <MemoryRouter initialEntries={["/projects/p1/team"]}>
        <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
          <AuthProvider>
            <ToastProvider>
              <Routes>
                <Route element={<Outlet context={{ project }} />}>
                  <Route path="/projects/:projectId/team" element={<TeamAccessPage />} />
                </Route>
              </Routes>
            </ToastProvider>
          </AuthProvider>
        </QueryClientProvider>
      </MemoryRouter>,
    );

  beforeEach(() => {
    // resetAllMocks strips implementations set in the mock factory, so they are set here or not at all.
    vi.resetAllMocks();
    vi.mocked(restoreSession).mockResolvedValue("token");
    vi.mocked(workspaceApi.members).mockResolvedValue([]);
  });

  const registry = {
    id: "c1",
    name: "Beta Client",
    sector: "Energy",
    hqCountry: null,
    domain: null,
    offLimitsNote: null,
    activeMandates: 1,
    deliveredMandates: 0,
    representatives: [],
    mandates: [],
  };

  it("lists the staff seats with their one role, and leaves client contacts out of the table", async () => {
    vi.mocked(authApi.me).mockResolvedValue(admin);
    vi.mocked(clientsApi.client).mockResolvedValue(registry);

    renderPage();

    expect(await screen.findByText("Alok Kumar")).toBeInTheDocument();
    expect(screen.getByText(/2 members$/)).toBeInTheDocument();

    // Sara's seat: Researcher held, Lead offered.
    const sara = screen.getByRole("radiogroup", { name: "Project role for Sara Al-Mansour" });
    expect(within(sara).getByRole("radio", { name: "Researcher" })).toBeChecked();
    expect(within(sara).getByRole("radio", { name: "Lead" })).not.toBeChecked();

    // The pure-client seat is a contact, not a team member — it has no role chips.
    expect(
      screen.queryByRole("radiogroup", { name: "Project role for Ext Rep" }),
    ).not.toBeInTheDocument();
  });

  it("moves a member's role with one call when the other chip is clicked", async () => {
    vi.mocked(authApi.me).mockResolvedValue(admin);
    vi.mocked(clientsApi.client).mockResolvedValue(registry);
    vi.mocked(projectsApi.putProjectMember).mockResolvedValue(project);

    renderPage();

    const sara = await screen.findByRole("radiogroup", { name: "Project role for Sara Al-Mansour" });
    await userEvent.click(within(sara).getByRole("radio", { name: "Lead" }));

    expect(projectsApi.putProjectMember).toHaveBeenCalledWith("p1", "m2", "LEAD");
  });

  it("locks the sole lead's row and offers removal for everyone else", async () => {
    vi.mocked(authApi.me).mockResolvedValue(admin);
    vi.mocked(clientsApi.client).mockResolvedValue(registry);
    vi.mocked(projectsApi.removeProjectMember).mockResolvedValue(project);

    renderPage();

    // Alok is the only lead: the server would refuse, so the row shows a padlock instead of a trash.
    expect(await screen.findAllByTitle("A mandate must keep a lead — make someone else lead first"))
      .not.toHaveLength(0);
    expect(screen.queryByLabelText("Remove Alok Kumar")).not.toBeInTheDocument();

    // The same invariant, said the same way one column left: demoting them would 409, so the chip
    // refuses the click rather than letting it through to a toast.
    const alok = screen.getByRole("radiogroup", { name: "Project role for Alok Kumar" });
    expect(within(alok).getByRole("radio", { name: "Researcher" })).toBeDisabled();

    await userEvent.click(screen.getByLabelText("Remove Sara Al-Mansour"));
    expect(projectsApi.removeProjectMember).toHaveBeenCalledWith("p1", "m2");
  });

  it("gives a researcher the read-only banner and no manage affordances", async () => {
    vi.mocked(authApi.me).mockResolvedValue(researcher);
    vi.mocked(clientsApi.client).mockResolvedValue(registry);

    renderPage();

    expect(
      await screen.findByText(
        "You have view-only access to team roles. Ask a project lead to make changes.",
      ),
    ).toBeInTheDocument();
    expect(screen.queryByText("Add team member")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Remove Sara Al-Mansour")).not.toBeInTheDocument();

    const sara = screen.getByRole("radiogroup", { name: "Project role for Sara Al-Mansour" });
    expect(within(sara).getByRole("radio", { name: "Lead" })).toBeDisabled();
  });

  it("seats a colleague from the directory at the role picked on their row", async () => {
    vi.mocked(authApi.me).mockResolvedValue(admin);
    vi.mocked(clientsApi.client).mockResolvedValue(registry);
    vi.mocked(workspaceApi.members).mockResolvedValue([
      {
        memberId: "m1",
        userId: "u1",
        fullName: "Alok Kumar",
        email: "alok@firm.example",
        title: null,
        avatarUrl: null,
        roles: ["ADMIN"],
        joinedAt: null,
      },
      {
        memberId: "m3",
        userId: "u3",
        fullName: "Omar Khalil",
        email: "omar@firm.example",
        title: null,
        avatarUrl: null,
        roles: ["MEMBER"],
        joinedAt: null,
      },
    ]);
    vi.mocked(projectsApi.putProjectMember).mockResolvedValue(project);

    renderPage();

    await userEvent.click(await screen.findByText("Add team member"));
    // Alok already staffs the mandate; only Omar is addable.
    expect(await screen.findByText("Omar Khalil")).toBeInTheDocument();
    expect(screen.queryByText("Alok Kumar", { selector: "div.text-\\[13px\\]" })).not.toBeInTheDocument();

    await userEvent.selectOptions(screen.getByLabelText("Role for Omar Khalil"), "LEAD");
    await userEvent.click(screen.getByRole("button", { name: "Add" }));

    expect(projectsApi.putProjectMember).toHaveBeenCalledWith("p1", "m3", "LEAD");
  });

  it("shows every attached contact with its attachment state, for an admin", async () => {
    vi.mocked(authApi.me).mockResolvedValue(admin);
    vi.mocked(clientsApi.client).mockResolvedValue({
      id: "c1",
      name: "Beta Client",
      sector: "Energy",
      hqCountry: null,
      domain: null,
      offLimitsNote: null,
      activeMandates: 1,
      deliveredMandates: 0,
      representatives: [],
      mandates: [],
    });

    renderPage();

    expect(await screen.findByText("Seated Rep")).toBeInTheDocument();
    expect(screen.getByText("Active")).toBeInTheDocument();
    expect(screen.getByText("Pending Rep")).toBeInTheDocument();
    expect(screen.getByText("Invite sent")).toBeInTheDocument();
    expect(screen.getByText("Add contact")).toBeInTheDocument();
  });

  it("detaches a contact from its trash button", async () => {
    vi.mocked(authApi.me).mockResolvedValue(admin);
    vi.mocked(clientsApi.client).mockResolvedValue({
      id: "c1",
      name: "Beta Client",
      sector: null,
      hqCountry: null,
      domain: null,
      offLimitsNote: null,
      activeMandates: 1,
      deliveredMandates: 0,
      representatives: [],
      mandates: [],
    });
    vi.mocked(projectsApi.detachRepresentative).mockResolvedValue({ ...project, representatives: [] });

    renderPage();

    await userEvent.click(await screen.findByLabelText("Remove Pending Rep"));
    expect(projectsApi.detachRepresentative).toHaveBeenCalledWith("p1", "r2");
  });

  it("marks already-attached people 'Added' in the modal and attaches the rest", async () => {
    vi.mocked(authApi.me).mockResolvedValue(admin);
    vi.mocked(clientsApi.client).mockResolvedValue({
      id: "c1",
      name: "Beta Client",
      sector: "Energy",
      hqCountry: null,
      domain: null,
      offLimitsNote: null,
      activeMandates: 1,
      deliveredMandates: 0,
      representatives: [
        { id: "r1", fullName: "Seated Rep", position: "Chair", email: "seated@beta-client.example", status: "ACTIVE" },
        { id: "r3", fullName: "Fresh Rep", position: null, email: "fresh@beta-client.example", status: "INVITED" },
      ],
      mandates: [],
    });
    vi.mocked(projectsApi.attachRepresentative).mockResolvedValue(project);

    renderPage();

    await userEvent.click(await screen.findByText("Add contact"));
    expect(await screen.findByText("Added")).toBeInTheDocument();

    await userEvent.click(screen.getByText("Add"));
    expect(projectsApi.attachRepresentative).toHaveBeenCalledWith("p1", "r3");
  });

  it("invites a new contact and attaches them in a single call", async () => {
    vi.mocked(authApi.me).mockResolvedValue(admin);
    vi.mocked(clientsApi.client).mockResolvedValue({
      id: "c1",
      name: "Beta Client",
      sector: "Energy",
      hqCountry: null,
      domain: null,
      offLimitsNote: null,
      activeMandates: 1,
      deliveredMandates: 0,
      representatives: [],
      mandates: [],
    });
    vi.mocked(projectsApi.inviteRepresentativeToProject).mockResolvedValue(project);

    renderPage();

    await userEvent.click(await screen.findByText("Add contact"));
    await userEvent.click(screen.getByRole("button", { name: "Invite by email" }));
    await userEvent.type(screen.getByPlaceholderText("e.g. Amir Haddad"), "Fresh Rep");
    await userEvent.type(screen.getByPlaceholderText("name@company.com"), "fresh@beta-client.example");
    await userEvent.click(screen.getByText("Send invite"));

    // One request, not a registry write followed by an attach that can fail on its own.
    expect(projectsApi.inviteRepresentativeToProject).toHaveBeenCalledWith("p1", {
      fullName: "Fresh Rep",
      position: undefined,
      email: "fresh@beta-client.example",
    });
    expect(clientsApi.inviteRepresentative).not.toHaveBeenCalled();
  });

  it("renders read-only for a pure client, without touching the client registry", async () => {
    vi.mocked(authApi.me).mockResolvedValue(pureClient);

    renderPage();

    expect(await screen.findByText("Seated Rep")).toBeInTheDocument();
    expect(screen.getByText("Pending Rep")).toBeInTheDocument();
    expect(screen.queryByText("Add contact")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Remove Seated Rep")).not.toBeInTheDocument();
    // The registry is a staff surface — a pure client's page must never request it.
    expect(clientsApi.client).not.toHaveBeenCalled();
  });
});
