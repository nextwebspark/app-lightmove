import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import type { Project, TeamMember } from "../api/types";
import { ProjectDrawer } from "./ProjectDrawer";

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
  positionTitle: "CFO Search",
  stage: "MAPPING",
  health: "RISK",
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

const renderDrawer = () =>
  render(
    <MemoryRouter>
      <Routes>
        <Route path="/" element={<ProjectDrawer project={project} onClose={vi.fn()} />} />
        <Route path="/projects/:projectId" element={<div>Position page</div>} />
      </Routes>
    </MemoryRouter>,
  );

describe("ProjectDrawer", () => {
  it("summarises the mandate's stage, staff roles and client contacts", () => {
    renderDrawer();

    expect(screen.getAllByText("Mapping")).not.toHaveLength(0);
    expect(screen.queryByText("At risk")).not.toBeInTheDocument();
    expect(screen.getByText("Lee Lead")).toBeInTheDocument();
    expect(screen.getByText("Lead")).toBeInTheDocument();
    expect(screen.getByText("Riley Researcher")).toBeInTheDocument();
    expect(screen.getByText("Researcher")).toBeInTheDocument();
    expect(screen.queryByText("Casey Contact")).not.toBeInTheDocument();
    expect(screen.getByText("Rita Rep")).toBeInTheDocument();
    expect(screen.getByText("Invite sent")).toBeInTheDocument();
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
  });

  it("opens the project", async () => {
    renderDrawer();

    await userEvent.click(screen.getByRole("button", { name: /open project/i }));

    expect(await screen.findByText("Position page")).toBeInTheDocument();
  });
});
