import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import { aUser, aWorkspace } from "../../../test/fixtures/user";
import { AuthProvider } from "../../auth/AuthProvider";
import * as authApi from "../../auth/api/authApi";
import * as projectsApi from "../../projects/api/projectsApi";
import type { Project } from "../../projects/api/types";
import * as tasksApi from "../../tasks/api/tasksApi";
import * as mailboxApi from "../../outreach/api/mailboxApi";
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
  getMyMeetingsToday: vi.fn(),
}));
vi.mock("../../outreach/api/mailboxApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../outreach/api/mailboxApi")>()),
  getMailbox: vi.fn(),
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

const connectedMailbox: mailboxApi.Mailbox = {
  offered: true,
  providers: ["google"],
  bookingLinkOffered: false,
  connection: {
    address: "yara@firm.example",
    provider: "google",
    status: "ACTIVE",
    dailyCap: 50,
    timeZone: "Asia/Dubai",
    connectedAt: "2026-10-01T09:00:00Z",
    movesOffNylas: false,
    runsStoppedByMove: 0,
    liveSequences: 0,
    livePeople: 0,
    readsReplies: true,
    reconnectToReadReplies: false,
    sentToday: 0,
    scheduledEmails: 0,
  },
};

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
    vi.mocked(homeApi.getMyOutreachNeeds).mockResolvedValue({ rows: [], truncated: false });
    vi.mocked(homeApi.getMyMeetingsToday).mockResolvedValue([]);
    vi.mocked(mailboxApi.getMailbox).mockResolvedValue(connectedMailbox);
  });

  it("ranks the caller's overdue task above a reply waiting, each with its position and client, a lead offered Team", async () => {
    vi.mocked(authApi.me).mockResolvedValue(aUser({ id: "u1", fullName: "Yara Haddad", workspace: aWorkspace({ roles: ["MEMBER"] }) }));
    vi.mocked(projectsApi.projects).mockResolvedValue([position("p1", "Product Planning", [seat("u1", "LEAD")])]);
    vi.mocked(homeApi.getMyOutreachNeeds).mockResolvedValue({
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

    const needs = await screen.findByRole("region", { name: "Needs your attention" });
    const list = await within(needs).findByRole("list", { name: "Needs your attention" });
    const items = within(list).getAllByRole("listitem");
    expect(items.map((item) => item.textContent)).toEqual([
      expect.stringContaining("Send profile to the hiring manager"),
      expect.stringContaining("Lina Farouk replied to your outreach"),
    ]);
    expect(items[0]).toHaveTextContent("Rania Aziz · Product Planning · Automotive");
    expect(items[0]).toHaveTextContent(/\d+d overdue/);
    expect(within(items[0]).getByRole("link", { name: "Send profile to the hiring manager" })).toHaveAttribute(
      "href",
      "/projects/p1/companies/universe?view=table&candidate=c2",
    );
    const reply = within(items[1]).getByRole("link", { name: /^Reply/ });
    expect(reply).toHaveAttribute("href", "/projects/p1/outreach?view=needs&who=mine&candidate=c9");
    expect(reply.className).not.toEqual(within(items[0]).getByRole("link", { name: /^Open/ }).className);
    expect(within(needs).getByRole("radio", { name: "Team" })).toBeInTheDocument();
    expect(screen.getByText(/2 actions need attention · Nothing on today's calendar · 1 active position/)).toBeInTheDocument();
    expect(screen.getByRole("region", { name: "Your positions" })).toHaveTextContent("Product Planning");
  });

  it("offers a researcher no Team view and nothing to customise", async () => {
    vi.mocked(authApi.me).mockResolvedValue(aUser({ id: "u1", workspace: aWorkspace({ roles: ["MEMBER"] }) }));
    vi.mocked(projectsApi.projects).mockResolvedValue([position("p1", "Product Planning", [seat("u1", "RESEARCHER")])]);

    renderHome();

    expect(await screen.findByText("You're all caught up")).toBeInTheDocument();
    expect(await screen.findByText("A clear day")).toBeInTheDocument();
    expect(screen.queryByText(/Connect your mailbox/)).not.toBeInTheDocument();
    expect(screen.queryByRole("radio", { name: "Team" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Customize/ })).not.toBeInTheDocument();
  });

  it("draws today's meetings on the timeline and points to the next executive's record", async () => {
    vi.mocked(authApi.me).mockResolvedValue(aUser({ id: "u1", workspace: aWorkspace({ roles: ["MEMBER"] }) }));
    vi.mocked(projectsApi.projects).mockResolvedValue([position("p1", "Buying Director", [seat("u1", "RESEARCHER")])]);
    const startsAt = new Date(Date.now() + 2 * 3_600_000);
    vi.mocked(homeApi.getMyMeetingsToday).mockResolvedValue([
      {
        id: "m1",
        title: "Screening",
        startsAt: startsAt.toISOString(),
        endsAt: new Date(startsAt.getTime() + 30 * 60_000).toISOString(),
        joinUrl: "https://meet.example/m1",
        videoProvider: "Google Meet",
        personId: "x5",
        personName: "Karim Nassar",
        projectId: "p1",
        candidateId: "c5",
        positionTitle: "Buying Director",
        clientName: "IKEA UAE",
      },
    ]);

    renderHome();

    const today = await screen.findByRole("region", { name: "Today" });
    const calendar = await within(today).findByRole("list", { name: "Today's calendar" });
    expect(calendar).toHaveTextContent("Screening · Karim Nassar");
    expect(calendar).toHaveTextContent("Buying Director · IKEA UAE");
    expect(calendar).toHaveTextContent("30 min · Google Meet");
    expect(within(today).getByText("Review Karim's profile and notes.")).toBeInTheDocument();
    expect(within(today).getByRole("link", { name: /View executive/ })).toHaveAttribute(
      "href",
      "/projects/p1/companies/universe?view=table&candidate=c5",
    );
  });

  it("lists the positions the caller is on with their next milestone, an overdue one called out", async () => {
    vi.mocked(authApi.me).mockResolvedValue(aUser({ id: "u1", workspace: aWorkspace({ roles: ["MEMBER"] }) }));
    vi.mocked(projectsApi.projects).mockResolvedValue([
      { ...position("p1", "Leasing Director", [seat("u1", "LEAD")]), health: "RISK", deliveryDate: "2020-01-07" },
      position("p2", "Not mine", []),
    ]);

    renderHome();

    const positions = await screen.findByRole("region", { name: "Your positions" });
    expect(positions).toHaveTextContent("1 active");
    expect(positions).toHaveTextContent("1 at risk");
    expect(positions).toHaveTextContent("Shortlist delivered overdue");
    expect(positions).not.toHaveTextContent("Not mine");
  });

  it("tells a new member with no position, no mailbox and nothing to do what will fill each card", async () => {
    vi.mocked(authApi.me).mockResolvedValue(aUser({ id: "u1", workspace: aWorkspace({ roles: ["MEMBER"] }) }));
    vi.mocked(projectsApi.projects).mockResolvedValue([]);
    vi.mocked(mailboxApi.getMailbox).mockResolvedValue({ ...connectedMailbox, connection: null });

    renderHome();

    expect(await screen.findByText("Nothing needs you yet")).toBeInTheDocument();
    expect(await screen.findByText("Connect your mailbox to see replies and delivery problems here.")).toBeInTheDocument();
    const today = screen.getByRole("region", { name: "Today" });
    expect(await within(today).findByText("No calendar connected")).toBeInTheDocument();
    expect(within(today).getByRole("link", { name: "Connect mailbox" })).toHaveAttribute("href", "/settings/integrations");
    const positions = screen.getByRole("region", { name: "Your positions" });
    expect(within(positions).getByText("You're not on any position yet")).toBeInTheDocument();
    expect(within(positions).getByRole("button", { name: "New position" })).toBeInTheDocument();
    expect(screen.getByText("Nothing needs your attention · Nothing on today's calendar · No positions yet")).toBeInTheDocument();
  });

  it("asks for the mailbox to be connected again when it has stopped working", async () => {
    vi.mocked(authApi.me).mockResolvedValue(aUser({ id: "u1", workspace: aWorkspace({ roles: ["MEMBER"] }) }));
    vi.mocked(projectsApi.projects).mockResolvedValue([position("p1", "Product Planning", [seat("u1", "RESEARCHER")])]);
    vi.mocked(mailboxApi.getMailbox).mockResolvedValue({
      ...connectedMailbox,
      connection: { ...connectedMailbox.connection!, status: "ERROR" },
    });

    renderHome();

    expect(await screen.findByText("Your mailbox needs reconnecting, so new replies may be missing.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Reconnect" })).toHaveAttribute("href", "/settings/integrations");
  });

  it("shows a client the positions shared with them, and nothing of the staff's queue", async () => {
    vi.mocked(authApi.me).mockResolvedValue(aUser({ workspace: aWorkspace({ roles: ["CLIENT"] }) }));
    vi.mocked(projectsApi.projects).mockResolvedValue([position("p1", "Product Planning", [])]);

    renderHome();

    expect(await screen.findByRole("region", { name: "Your positions" })).toHaveTextContent("Product Planning");
    expect(screen.queryByRole("region", { name: "Needs your attention" })).not.toBeInTheDocument();
    expect(homeApi.getMyOutreachNeeds).not.toHaveBeenCalled();
    expect(tasksApi.getMyTasks).not.toHaveBeenCalled();
  });

  it("walks an admin of a new workspace through setting it up", async () => {
    vi.mocked(authApi.me).mockResolvedValue(aUser({ workspace: aWorkspace({ name: "Meridian", roles: ["ADMIN"] }) }));
    vi.mocked(projectsApi.projects).mockResolvedValue([]);

    renderHome();

    expect(await screen.findByText("Set up Meridian")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "New position" })).toBeInTheDocument();
    expect(screen.queryByRole("region", { name: "Needs your attention" })).not.toBeInTheDocument();
  });
});
