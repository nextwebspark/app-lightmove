import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { sampleProgress } from "../../../test/sampleProject";
import * as projectsApi from "../api/projectsApi";
import type { Project, TeamMember } from "../api/types";
import { ProjectDrawer } from "./ProjectDrawer";

vi.mock("../api/projectsApi");

const seat = (memberId: string, fullName: string, projectRoles: TeamMember["projectRoles"]): TeamMember => ({
  memberId,
  userId: `u-${memberId}`,
  fullName,
  avatarUrl: null,
  workspaceRoles: ["MEMBER"],
  projectRoles,
});

const project: Project = {
  id: "p1",
  clientId: "c1",
  clientName: "Beta Client",
  clientLogoUrl: "https://logo.example/beta-client.png",
  positionTitle: "CFO Search",
  stage: "MAPPING",
  health: "RISK",
  projectType: "MAPPING",
  startDate: "2026-07-01",
  mappingTargetDate: "2026-10-30",
  shortlistTargetDate: null,
  progress: sampleProgress({
    mapPercent: 25,
    universeCompanies: 4,
    companiesResearched: 1,
    candidatesMapped: 2,
    qualifiedMatches: 1,
    mappingVelocityPerWeek: 2.1,
    governingMilestone: "2026-10-30",
    daysRemaining: 18,
  }),
  targetDate: null,
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
      email: "rita@beta-client.example",
      status: "INVITED",
    },
  ],
  companies: 4,
  candidates: 2,
  createdAt: "2026-07-13T10:00:00Z",
};

const renderDrawer = (overrides: Partial<Project> = {}) =>
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter>
        <Routes>
          <Route
            path="/"
            element={<ProjectDrawer project={{ ...project, ...overrides }} onClose={vi.fn()} />}
          />
          <Route path="/projects/:projectId" element={<div>Position page</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );

describe("ProjectDrawer", () => {
  beforeEach(() => {
    vi.mocked(projectsApi.projectActivityKey).mockImplementation(
      (projectId: string) => ["project-activity", projectId] as never,
    );
    vi.mocked(projectsApi.projectActivity).mockResolvedValue([
      {
        eventType: "CANDIDATE_ADDED",
        summary: "mapped an executive",
        actorName: "Lee Lead",
        actorAvatarUrl: null,
        occurredAt: "2026-07-13T10:00:00Z",
      },
    ]);
  });

  it("summarises the mandate's stage, staff roles and client contacts", () => {
    renderDrawer();

    expect(screen.getAllByText("Mapping")).not.toHaveLength(0);
    expect(screen.getByText("Lee Lead")).toBeInTheDocument();
    expect(screen.getByText("Lead")).toBeInTheDocument();
    expect(screen.getByText("Riley Researcher")).toBeInTheDocument();
    expect(screen.getByText("Researcher")).toBeInTheDocument();
    expect(screen.queryByText("Casey Contact")).not.toBeInTheDocument();
    expect(screen.getByText("Rita Rep")).toBeInTheDocument();
    expect(screen.getByText("Invite sent")).toBeInTheDocument();
  });

  it("reports the mandate's universe and the executives mapped against it", () => {
    renderDrawer();

    expect(screen.getByText("Universe companies").closest("div")).toHaveTextContent("4");
    expect(screen.getByText("Executives mapped").closest("div")).toHaveTextContent("2");
    expect(screen.getByText("Qualified matches").closest("div")).toHaveTextContent("1");
    expect(screen.getByText("Mapping velocity").closest("div")).toHaveTextContent("2.1/wk");
  });

  it("states what the mandate is engaged to deliver, and how it is doing", () => {
    renderDrawer();

    expect(screen.getByText("Mapping only")).toBeInTheDocument();
    expect(screen.getByText("At risk")).toBeInTheDocument();
    expect(screen.getByText("Map · 25%")).toBeInTheDocument();
    expect(screen.getByText("18 days remaining")).toBeInTheDocument();
    expect(screen.getByText("Map 30 Oct 2026")).toBeInTheDocument();
  });

  it("explains a mandate that is behind", () => {
    renderDrawer();

    expect(screen.getByRole("status")).toHaveTextContent("1 of 4 companies researched");
  });

  it("says nothing about pace on a mandate that is keeping up", () => {
    renderDrawer({ health: "OK" });

    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });

  it("narrates what the ledger recorded against the mandate", async () => {
    renderDrawer();

    expect(await screen.findByText("Lee Lead mapped an executive")).toBeInTheDocument();
  });

  it("shows the client's logo", () => {
    const { container } = renderDrawer();

    expect(container.querySelector('img[src="https://logo.example/beta-client.png"]')).toBeInTheDocument();
  });

  it("offers no controls that change the team", () => {
    renderDrawer();

    expect(screen.queryByRole("switch")).not.toBeInTheDocument();
    expect(screen.queryByRole("radio")).not.toBeInTheDocument();
  });

  it("sends team and client changes to the project's settings", () => {
    renderDrawer();

    expect(screen.getByRole("link", { name: /manage team & client access/i })).toHaveAttribute(
      "href",
      "/projects/p1/team",
    );

    const [teamInvite, clientInvite] = screen.getAllByRole("link", { name: "+ Invite" });
    expect(teamInvite).toHaveAttribute("href", "/projects/p1/team?invite=team");
    expect(clientInvite).toHaveAttribute("href", "/projects/p1/team?invite=client");
  });

  it("opens the project", async () => {
    renderDrawer();

    await userEvent.click(screen.getByRole("button", { name: /open project/i }));

    expect(await screen.findByText("Position page")).toBeInTheDocument();
  });
});
