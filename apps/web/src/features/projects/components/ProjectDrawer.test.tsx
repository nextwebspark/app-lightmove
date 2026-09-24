import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { WorkspaceRole } from "../../auth/api/types";
import * as projectsApi from "../api/projectsApi";
import type { Project, ProjectActivityEntry, TeamMember } from "../api/types";
import { ProjectDrawer } from "./ProjectDrawer";

vi.mock("../api/projectsApi", async (importOriginal) => ({
  ...(await importOriginal<typeof projectsApi>()),
  projectActivity: vi.fn(),
}));

const viewer: { id: string; workspace: { roles: WorkspaceRole[] } } = { id: "u-m2", workspace: { roles: ["MEMBER"] } };
vi.mock("../../auth/AuthProvider", () => ({ useAuth: () => ({ user: viewer }) }));

const seat = (memberId: string, fullName: string, projectRoles: TeamMember["projectRoles"]): TeamMember => ({
  memberId,
  userId: `u-${memberId}`,
  fullName,
  avatarUrl: null,
  workspaceRoles: ["MEMBER"],
  projectRoles,
});

const daysFromToday = (days: number) => {
  const date = new Date();
  date.setDate(date.getDate() + days);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
};

const project: Project = {
  id: "p1",
  clientId: "c1",
  clientName: "Automotive",
  clientLogoUrl: null,
  positionTitle: "CFO Search",
  stage: "MAPPING",
  health: "RISK",
  targetDate: daysFromToday(10),
  projectType: "SEARCH",
  startDate: null,
  deliveryDate: null,
  mappingTargetDate: null,
  team: [
    seat("m1", "Riley Researcher", ["RESEARCHER"]),
    seat("m2", "Lee Lead", ["LEAD"]),
    seat("m3", "Casey Contact", ["CLIENT"]),
  ],
  representatives: [
    {
      representativeId: "r1",
      fullName: "Rita Rep",
      position: "HR Director",
      email: "rita@automotive.example",
      status: "INVITED",
    },
  ],
  companies: 8,
  candidates: 6,
  mappedCandidates: 8,
  engagedCandidates: 2,
  mappedCompanies: 3,
  createdAt: "2026-07-13T10:00:00Z",
};

const entry = (id: number, type: string, details: Record<string, string> = {}): ProjectActivityEntry => ({
  id,
  type,
  occurredAt: new Date().toISOString(),
  actorUserId: "u-m2",
  actorName: "Lee Lead",
  actorAvatarUrl: null,
  details,
});

const renderDrawer = (shown: Project = project) =>
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter>
        <Routes>
          <Route path="/" element={<ProjectDrawer project={shown} onClose={vi.fn()} />} />
          <Route path="/projects/:projectId" element={<div>Position page</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );

describe("ProjectDrawer", () => {
  beforeEach(() => {
    viewer.id = "u-m2";
    viewer.workspace.roles = ["MEMBER"];
    vi.mocked(projectsApi.projectActivity).mockReset();
    vi.mocked(projectsApi.projectActivity).mockResolvedValue({ entries: [], nextCursor: null });
  });

  it("summarises the position's stage, health, staff roles and hiring managers", () => {
    renderDrawer();

    expect(screen.getAllByText("Mapping")).not.toHaveLength(0);
    expect(screen.getByText("At risk")).toBeInTheDocument();
    expect(screen.getByText("Lee Lead")).toBeInTheDocument();
    expect(screen.getByText("Lead")).toBeInTheDocument();
    expect(screen.getByText("Researcher")).toBeInTheDocument();
    expect(screen.queryByText("Casey Contact")).not.toBeInTheDocument();
    expect(screen.getByText("Rita Rep")).toBeInTheDocument();
    expect(screen.getByText("Invite sent")).toBeInTheDocument();
  });

  it("measures progress as universe companies with an executive mapped", () => {
    renderDrawer();

    const bar = screen.getByRole("progressbar");
    expect(bar).toHaveAttribute("aria-valuenow", "38");
    expect(screen.getByText(/with an executive mapped/)).toHaveTextContent("3 of 8 companies with an executive mapped");
    expect(screen.getByText("10 days remaining")).toBeInTheDocument();
    expect(screen.getByText(/38% of the universe is mapped/)).toBeInTheDocument();
  });

  it("reports the key metrics from the list's counts", () => {
    renderDrawer();

    expect(screen.getByText("Universe companies").closest("div")).toHaveTextContent("8");
    // Everyone mapped, not the six still in play: a refusal must not shrink what was mapped.
    expect(screen.getByText("Executives mapped").closest("div")).toHaveTextContent("8");
    expect(screen.getByText("Engaged").closest("div")).toHaveTextContent("2");
  });

  it("reads an overdue target as overdue", () => {
    renderDrawer({ ...project, health: "OFF", targetDate: daysFromToday(-3) });

    expect(screen.getByText("3 days overdue")).toBeInTheDocument();
    expect(screen.getByText(/passed 3 days ago/)).toBeInTheDocument();
  });

  it("marks the current stage gate", () => {
    renderDrawer();

    expect(screen.getByRole("listitem", { current: "step" })).toHaveTextContent("Mapping");
  });

  it("merges a run of one person's work into one activity line and pages on See more", async () => {
    vi.mocked(projectsApi.projectActivity)
      .mockResolvedValueOnce({
        entries: [
          entry(9, "CANDIDATE_ADDED"),
          entry(8, "CANDIDATE_ADDED"),
          entry(7, "TRIAGE_BULK_ADDED", { added: "12", status: "DECLINED" }),
          entry(6, "POSITION_PUBLISHED"),
          entry(5, "TRIAGE_COMPANY_REMOVED", { companyName: "Gulf Trader" }),
          entry(4, "PROJECT_UPDATED"),
        ],
        nextCursor: 4,
      })
      .mockResolvedValueOnce({ entries: [entry(3, "PROJECT_CREATED")], nextCursor: null });
    renderDrawer();

    expect(await screen.findByText("mapped 2 executives")).toBeInTheDocument();
    expect(screen.getByText("declined 12 companies")).toBeInTheDocument();
    expect(screen.getByText("published the brief")).toBeInTheDocument();
    expect(screen.getByText("removed Gulf Trader")).toBeInTheDocument();
    expect(screen.queryByText("created the position")).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "See more" }));

    expect(await screen.findByText("created the position")).toBeInTheDocument();
    expect(projectsApi.projectActivity).toHaveBeenLastCalledWith("p1", 4);
    expect(screen.queryByRole("button", { name: "See more" })).not.toBeInTheDocument();
  });

  it("keeps activity and invite links from a client representative", async () => {
    viewer.id = "u-m3";
    renderDrawer();

    expect(await screen.findByText("Rita Rep")).toBeInTheDocument();
    expect(screen.queryByText("Recent activity")).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "+ Invite" })).not.toBeInTheDocument();
    expect(projectsApi.projectActivity).not.toHaveBeenCalled();
  });

  it("stops reading on its own after a few pages that fold into one line", async () => {
    let cursor = 100;
    vi.mocked(projectsApi.projectActivity).mockImplementation(async () => {
      cursor -= 1;
      return { entries: [entry(cursor, "CANDIDATE_ADDED")], nextCursor: cursor };
    });
    renderDrawer();

    await waitFor(() => expect(projectsApi.projectActivity).toHaveBeenCalledTimes(4));
    await new Promise((settle) => setTimeout(settle, 50));
    expect(projectsApi.projectActivity).toHaveBeenCalledTimes(4);
    expect(screen.getByRole("button", { name: "See more" })).toBeEnabled();
  });

  it("shows a researcher the activity but no way to change who is on the position", async () => {
    viewer.id = "u-m1";
    renderDrawer();

    expect(await screen.findByText("Recent activity")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "+ Invite" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /manage team & hiring manager access/i })).not.toBeInTheDocument();
  });

  it("offers no controls that change the team", () => {
    renderDrawer();

    expect(screen.queryByRole("switch")).not.toBeInTheDocument();
    expect(screen.queryByRole("radio")).not.toBeInTheDocument();
  });

  it("sends team and hiring-manager changes to the position's settings", () => {
    renderDrawer();

    expect(screen.getByRole("link", { name: /manage team & hiring manager access/i })).toHaveAttribute(
      "href",
      "/projects/p1/team",
    );
    for (const invite of screen.getAllByRole("link", { name: "+ Invite" })) {
      expect(invite).toHaveAttribute("href", "/projects/p1/team");
    }
  });

  it("opens the position", async () => {
    renderDrawer();

    await userEvent.click(screen.getByRole("link", { name: /open position/i }));

    expect(await screen.findByText("Position page")).toBeInTheDocument();
  });
});
