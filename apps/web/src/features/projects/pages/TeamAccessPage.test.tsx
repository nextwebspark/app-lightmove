import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Outlet, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import { AuthProvider } from "../../auth/AuthProvider";
import * as authApi from "../../auth/api/authApi";
import * as clientsApi from "../../clients/api/clientsApi";
import * as projectsApi from "../api/projectsApi";
import type { Project } from "../api/types";
import { TeamAccessPage } from "./TeamAccessPage";

vi.mock("../../auth/api/authApi");
vi.mock("../api/projectsApi", async (importOriginal) => ({
  // Keys are real; only the calls are mocked.
  ...(await importOriginal<typeof import("../api/projectsApi")>()),
  attachRepresentative: vi.fn(),
  detachRepresentative: vi.fn(),
}));
vi.mock("../../clients/api/clientsApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../clients/api/clientsApi")>()),
  client: vi.fn(),
}));
vi.mock("../../../lib/apiClient", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../../lib/apiClient")>()),
  restoreSession: vi.fn(),
  setAccessToken: vi.fn(),
}));

const { restoreSession } = await import("../../../lib/apiClient");

/**
 * The Team & access Client section: attached contacts with their attachment state, the manage
 * affordances behind the PROJECT_EDIT mirror, and the pure-client rendering that must never touch the
 * staff-only client registry.
 */
describe("TeamAccessPage", () => {
  const admin = {
    id: "u1",
    email: "alok@firm.example",
    fullName: "Alok Kumar",
    title: null,
    avatarUrl: null,
    emailVerified: true,
    onboardingHeld: false,
    pendingInvitation: null,
    workspace: {
      id: "w1",
      name: "Firm",
      slug: "firm",
      logoMark: "F",
      emailDomain: "firm.example",
      roles: ["ADMIN" as const],
    },
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
        workspaceRoles: ["ADMIN"],
        projectRoles: ["ADMIN", "LEAD"],
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

    await userEvent.click(screen.getByText("Invite"));
    expect(projectsApi.attachRepresentative).toHaveBeenCalledWith("p1", "r3");
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
