import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import { AuthProvider } from "../../auth/AuthProvider";
import * as authApi from "../../auth/api/authApi";
import * as clientsApi from "../../clients/api/clientsApi";
import * as workspaceApi from "../../workspace/api/workspaceApi";
import * as projectsApi from "../api/projectsApi";
import type { Project } from "../api/types";
import { ProjectsPage } from "./ProjectsPage";

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
    emailVerified: true,
    onboardingHeld: false,
    pendingInvitation: null,
    workspace: {
      id: "w1",
      name: "Access Firm",
      slug: "access-firm",
      logoMark: "A",
      emailDomain: "access-firm.com",
      roles: ["CLIENT" as const],
    },
  };

  const attachedMandate: Project = {
    id: "p1",
    clientId: "c1",
    clientName: "Beta Client",
    positionTitle: "CFO Search",
    stage: "MAPPING",
    health: "OK",
    targetDate: null,
    team: [],
    representatives: [],
    companies: 0,
    candidates: 0,
    createdAt: "2026-07-13T10:00:00Z",
  };

  const renderPage = () =>
    render(
      <MemoryRouter>
        <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
          <AuthProvider>
            <ToastProvider>
              <ProjectsPage view="my" />
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

    expect(await screen.findByText("CFO Search")).toBeInTheDocument();
    expect(screen.queryByText("New project")).not.toBeInTheDocument();
    // The registry and roster are staff surfaces — a pure client must never request them.
    expect(clientsApi.clients).not.toHaveBeenCalled();
    expect(workspaceApi.members).not.toHaveBeenCalled();
  });

  it("shows the no-projects-shared state, with nothing to create, when no mandate is attached", async () => {
    vi.mocked(projectsApi.projects).mockResolvedValue([]);

    renderPage();

    expect(await screen.findByText("No projects shared with you yet")).toBeInTheDocument();
    expect(screen.queryByText("New project")).not.toBeInTheDocument();
    expect(screen.queryByText("Create your first project")).not.toBeInTheDocument();
  });
});
