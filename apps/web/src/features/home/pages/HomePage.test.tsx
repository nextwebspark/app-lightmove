import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import { aUser, aWorkspace } from "../../../test/fixtures/user";
import { AuthProvider } from "../../auth/AuthProvider";
import * as authApi from "../../auth/api/authApi";
import * as projectsApi from "../../projects/api/projectsApi";
import type { Project } from "../../projects/api/types";
import * as tasksApi from "../../tasks/api/tasksApi";
import * as homeApi from "../api/homeApi";
import { HomePage } from "./HomePage";

vi.mock("../../auth/api/authApi");
vi.mock("../../projects/api/projectsApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../projects/api/projectsApi")>()),
  projects: vi.fn(),
}));
vi.mock("../../tasks/api/tasksApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../tasks/api/tasksApi")>()),
  getMyTasks: vi.fn(),
}));
vi.mock("../api/homeApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/homeApi")>()),
  getMyOutreachNeeds: vi.fn(),
}));
vi.mock("../../dashboards/api/dashboardsApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../dashboards/api/dashboardsApi")>()),
  getChartLibrary: async () => [],
  runChartQuery: async () => {
    throw new Error("not in this test");
  },
}));
vi.mock("../../clients/api/clientsApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../clients/api/clientsApi")>()),
  clients: async () => [],
}));
vi.mock("../../../lib/apiClient", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../../lib/apiClient")>()),
  restoreSession: vi.fn(),
  setAccessToken: vi.fn(),
}));

const { restoreSession } = await import("../../../lib/apiClient");

const seat = (userId: string, role: "LEAD" | "RESEARCHER") => ({
  memberId: `m-${userId}`,
  userId,
  fullName: "Yara Haddad",
  avatarUrl: null,
  workspaceRoles: ["MEMBER" as const],
  projectRoles: [role],
});

const position = (id: string, title: string, team: ReturnType<typeof seat>[]): Project => ({
  id,
  clientId: "c1",
  clientName: "Automotive",
  clientLogoUrl: null,
  positionTitle: title,
  stage: "MAPPING",
  health: "OK",
  targetDate: "2026-11-30",
  projectType: "SEARCH",
  startDate: null,
  deliveryDate: null,
  mappingTargetDate: null,
  team,
  representatives: [],
  companies: 19,
  candidates: 14,
  mappedCandidates: 14,
  engagedCandidates: 3,
  mappedCompanies: 19,
  createdAt: "2026-07-13T10:00:00Z",
  archivedAt: null,
  archivedByName: null,
});

const noTasks = { tasks: [], counts: { today: 0, overdue: 0, upcoming: 0, assignedByMe: 0 }, hasMore: false };

function renderHome() {
  return render(
    <MemoryRouter>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <AuthProvider>
          <ToastProvider>
            <HomePage />
          </ToastProvider>
        </AuthProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

describe("HomePage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(restoreSession).mockResolvedValue("token");
    vi.mocked(tasksApi.getMyTasks).mockResolvedValue(noTasks);
    vi.mocked(homeApi.getMyOutreachNeeds).mockResolvedValue({ rows: [], total: 0, truncated: false });
  });
  afterEach(() => localStorage.clear());

  it("gathers a reply waiting and the caller's own task into Needs you, a lead offered Mine and Team", async () => {
    vi.mocked(authApi.me).mockResolvedValue(aUser({ id: "u1", fullName: "Yara Haddad", workspace: aWorkspace({ roles: ["MEMBER"] }) }));
    vi.mocked(projectsApi.projects).mockResolvedValue([position("p1", "Product Planning", [seat("u1", "LEAD")])]);
    vi.mocked(homeApi.getMyOutreachNeeds).mockResolvedValue({
      total: 1,
      truncated: false,
      rows: [
        {
          projectId: "p1",
          positionTitle: "Product Planning",
          row: {
            candidateId: "c9",
            personId: "x9",
            fullName: "Lina Farouk",
            title: "Planning Director",
            companyName: "Nissan Gulf",
            enrichedAt: null,
            status: "replied",
            statusLabel: "Replied",
            statusColour: "green",
            group: "NEEDS_REPLY",
            action: null,
            run: null,
            latest: null,
            lastSentAt: null,
            scheduledAt: null,
            lastActivityAt: "2026-10-14T08:00:00Z",
          },
        },
      ],
    });
    vi.mocked(tasksApi.getMyTasks).mockImplementation(async (view) =>
      view === "overdue"
        ? {
            ...noTasks,
            tasks: [
              {
                id: "t1",
                projectId: "p1",
                projectTitle: "Product Planning",
                candidateId: "c2",
                personId: "x2",
                personName: "Rania Aziz",
                title: "Send profile to the hiring manager",
                kind: "email",
                detail: null,
                presetId: null,
                note: null,
                assignee: { userId: "u1", fullName: "Yara Haddad", avatarUrl: null },
                createdBy: null,
                dueAt: "2026-10-03T13:00:00Z",
                status: "open",
                source: "manual",
                rule: null,
                completedAt: null,
                completedBy: null,
                createdAt: "2026-10-01T09:00:00Z",
                editable: true,
              },
            ],
          }
        : noTasks,
    );

    renderHome();

    const needs = await screen.findByRole("region", { name: "Needs you" });
    expect(await within(needs).findByText("Lina Farouk")).toBeInTheDocument();
    expect(within(needs).getByRole("link", { name: "Reply" })).toHaveAttribute(
      "href",
      "/projects/p1/outreach?candidate=c9&who=mine",
    );
    expect(await within(needs).findByText("Send profile to the hiring manager")).toBeInTheDocument();
    expect(within(needs).getByRole("radio", { name: "Team" })).toBeInTheDocument();
    expect(screen.getByRole("region", { name: "Your positions" })).toHaveTextContent("Product Planning");
  });

  it("lets the person hide a section, and keeps their choice", async () => {
    vi.mocked(authApi.me).mockResolvedValue(aUser({ id: "u1", workspace: aWorkspace({ roles: ["MEMBER"] }) }));
    vi.mocked(projectsApi.projects).mockResolvedValue([position("p1", "Product Planning", [seat("u1", "RESEARCHER")])]);
    const user = userEvent.setup();

    renderHome();
    await screen.findByRole("region", { name: "Your positions" });
    expect(screen.queryByRole("radio", { name: "Team" })).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /Customize/ }));
    await user.click(screen.getByRole("switch", { name: "Show Your positions" }));

    expect(screen.queryByRole("region", { name: "Your positions" })).not.toBeInTheDocument();
    expect(JSON.parse(localStorage.getItem("lm-home") ?? "{}").sections).toContainEqual({ key: "positions", on: false });
  });

  it("shows a client the positions shared with them, and nothing of the staff's queue", async () => {
    vi.mocked(authApi.me).mockResolvedValue(aUser({ workspace: aWorkspace({ roles: ["CLIENT"] }) }));
    vi.mocked(projectsApi.projects).mockResolvedValue([position("p1", "Product Planning", [])]);

    renderHome();

    expect(await screen.findByRole("region", { name: "Your positions" })).toHaveTextContent("Product Planning");
    expect(screen.queryByRole("region", { name: "Needs you" })).not.toBeInTheDocument();
    expect(homeApi.getMyOutreachNeeds).not.toHaveBeenCalled();
    expect(tasksApi.getMyTasks).not.toHaveBeenCalled();
  });

  it("walks an admin of a new workspace through setting it up", async () => {
    vi.mocked(authApi.me).mockResolvedValue(aUser({ workspace: aWorkspace({ name: "Meridian", roles: ["ADMIN"] }) }));
    vi.mocked(projectsApi.projects).mockResolvedValue([]);

    renderHome();

    expect(await screen.findByText("Set up Meridian")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "New position" })).toBeInTheDocument();
    expect(screen.queryByRole("region", { name: "Needs you" })).not.toBeInTheDocument();
  });
});
