import { render, screen, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { WorkspaceRole } from "../../auth/api/types";
import type { AttachedRepresentative, Project, TeamMember } from "../api/types";
import { ProjectPeopleBar } from "./ProjectPeopleBar";

const viewer: { id: string; workspace: { roles: WorkspaceRole[] } } = { id: "u-m1", workspace: { roles: ["MEMBER"] } };
vi.mock("../../auth/AuthProvider", () => ({ useAuth: () => ({ user: viewer }) }));

const seat = (memberId: string, fullName: string, projectRoles: TeamMember["projectRoles"]): TeamMember => ({
  memberId,
  userId: `u-${memberId}`,
  fullName,
  avatarUrl: null,
  workspaceRoles: projectRoles.includes("CLIENT") ? ["CLIENT"] : ["MEMBER"],
  projectRoles,
});

const rep = (id: string, fullName: string, status: AttachedRepresentative["status"]): AttachedRepresentative => ({
  representativeId: id,
  fullName,
  position: null,
  email: `${id}@client.example`,
  avatarUrl: status === "ACTIVE" ? `https://pics.example/${id}.jpg` : null,
  status,
});

const project = (overrides: Partial<Project>): Project =>
  ({
    id: "p1",
    clientId: "c1",
    clientName: "Automotive",
    clientLogoUrl: null,
    positionTitle: "CFO Search",
    team: [],
    representatives: [],
    ...overrides,
  }) as Project;

const renderBar = (value: Project) =>
  render(
    <MemoryRouter>
      <ProjectPeopleBar project={value} />
    </MemoryRouter>,
  );

describe("ProjectPeopleBar", () => {
  beforeEach(() => {
    viewer.id = "u-m1";
    viewer.workspace.roles = ["MEMBER"];
  });

  const staffed = [
    seat("m2", "Omar Khalid", ["RESEARCHER"]),
    seat("m1", "Leila Hassan", ["LEAD"]),
    seat("m3", "Sara Nasser", ["RESEARCHER"]),
    seat("m4", "Faisal Obaid", ["RESEARCHER"]),
    seat("m5", "Maya Rahman", ["RESEARCHER"]),
    seat("m6", "Yusuf Ali", ["RESEARCHER"]),
    seat("r1", "Jamal Rashid", ["CLIENT"]),
  ];

  it("draws the staff alone under Team, lead first, folding the rest into a count", () => {
    renderBar(project({ team: staffed, representatives: [rep("r1", "Jamal Rashid", "ACTIVE")] }));

    const team = screen.getByRole("link", { name: "Team: 6 people. Open Team & access" });
    expect(team).toHaveAttribute("href", "/projects/p1/team");
    // jsdom applies no CSS, so every breakpoint's stack is present; the desktop one is the last.
    const desktop = within(team).getAllByTitle(/·/).slice(-4);
    expect(desktop.map((avatar) => avatar.getAttribute("title"))).toEqual([
      "Leila Hassan · Lead",
      "Omar Khalid · Researcher",
      "Sara Nasser · Researcher",
      "Faisal Obaid · Researcher",
    ]);
    expect(within(team).getByText("+2")).toHaveAttribute("title", "Maya Rahman, Yusuf Ali");
    expect(within(team).queryByTitle("Jamal Rashid · Researcher")).not.toBeInTheDocument();
  });

  it("shows seated hiring managers with their picture and counts the invited ones", () => {
    renderBar(
      project({
        team: staffed,
        representatives: [
          rep("r1", "Jamal Rashid", "ACTIVE"),
          rep("r2", "Nora Saeed", "INVITED"),
          rep("r3", "Karim Aziz", "INVITED"),
        ],
      }),
    );

    const clients = screen.getByRole("link", { name: "Hiring managers: 1 person, 2 invited. Open Team & access" });
    expect(within(clients).getByText("2 invited")).toBeInTheDocument();
    expect(within(clients).getByAltText("Jamal Rashid")).toHaveAttribute("src", "https://pics.example/r1.jpg");
    expect(within(clients).queryByText(/Nora|Karim/)).not.toBeInTheDocument();
  });

  it("says so when the mandate has no hiring managers at all", () => {
    renderBar(project({ team: staffed }));

    const clients = screen.getByRole("link", { name: "Hiring managers: 0 people. Open Team & access" });
    expect(within(clients).getByText("No client rep")).toBeInTheDocument();
  });

  it("offers Invite to the lead, pointing at Team & access", () => {
    renderBar(project({ team: staffed }));

    expect(screen.getByRole("link", { name: "Invite team members or hiring managers" })).toHaveAttribute(
      "href",
      "/projects/p1/team",
    );
  });

  it("offers Invite to a workspace admin who holds no seat", () => {
    viewer.id = "u-elsewhere";
    viewer.workspace.roles = ["ADMIN"];
    renderBar(project({ team: staffed }));

    expect(screen.getByRole("link", { name: "Invite team members or hiring managers" })).toBeInTheDocument();
  });

  it.each([
    ["a researcher", "u-m2", ["MEMBER"] as WorkspaceRole[]],
    ["a hiring manager", "u-r1", ["CLIENT"] as WorkspaceRole[]],
  ])("does not offer Invite to %s, who cannot seat anyone", (_, id, roles) => {
    viewer.id = id;
    viewer.workspace.roles = roles;
    renderBar(project({ team: staffed }));

    expect(screen.queryByRole("link", { name: /Invite/ })).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: /^Team:/ })).toBeInTheDocument();
  });
});
