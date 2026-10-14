import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import * as projectsApi from "../../projects/api/projectsApi";
import type { Project, TeamMember } from "../../projects/api/types";
import * as tasksApi from "../../tasks/api/tasksApi";
import type { TaskResponse } from "../../tasks/api/types";
import * as personCrmApi from "../api/personCrmApi";
import type { PersonNote } from "../api/types";
import { NotesSection } from "./PersonSections";

vi.mock("../../auth/AuthProvider", () => ({
  useAuth: () => ({
    user: { id: "u1", fullName: "Yara Haddad", avatarUrl: null, workspace: { roles: ["MEMBER"], mode: "COMPANY" } },
  }),
}));
vi.mock("../api/personCrmApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/personCrmApi")>()),
  getPersonNotes: vi.fn(),
  getPersonPositions: vi.fn(),
  writePersonNote: vi.fn(),
}));
vi.mock("../../projects/api/projectsApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../projects/api/projectsApi")>()),
  projects: vi.fn(),
}));
vi.mock("../../tasks/api/tasksApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../tasks/api/tasksApi")>()),
  createTask: vi.fn(),
}));

const seat = (userId: string, fullName: string, projectRoles: TeamMember["projectRoles"]): TeamMember => ({
  memberId: `m-${userId}`,
  userId,
  fullName,
  avatarUrl: null,
  workspaceRoles: ["MEMBER"],
  projectRoles,
});

const position = {
  id: "p1",
  positionTitle: "Chief Financial Officer",
  team: [
    seat("u1", "Yara Haddad", ["LEAD"]),
    seat("u3", "Sara Al-Mansour", ["RESEARCHER"]),
    seat("u4", "Omar Khalil", ["LEAD"]),
    seat("u9", "Khalid Al Marzooqi", ["CLIENT"]),
  ],
} as Project;

const aNote = (body: string): PersonNote => ({
  id: "n1",
  kind: "general",
  body,
  pinned: false,
  projectId: "p1",
  projectTitle: "Chief Financial Officer",
  authorUserId: "u1",
  authorName: "Yara Haddad",
  authorAvatarUrl: null,
  createdAt: "2026-10-03T08:00:00Z",
  editedAt: null,
  editedByName: null,
  editable: true,
});

function renderNotes() {
  render(
    <MemoryRouter>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <ToastProvider>
          <NotesSection projectId="p1" candidateId="c1" />
        </ToastProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

const composer = () => screen.getByRole("textbox", { name: "New note" });

describe("the note composer", () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date(2026, 9, 3, 10, 0));
    vi.mocked(personCrmApi.getPersonNotes).mockResolvedValue([]);
    vi.mocked(personCrmApi.getPersonPositions).mockResolvedValue([]);
    vi.mocked(personCrmApi.writePersonNote).mockImplementation(async (_p, _c, note) => aNote(note.body));
    vi.mocked(projectsApi.projects).mockResolvedValue([position]);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("lists @team then the team but me on @, and writes the one picked as a token", async () => {
    renderNotes();
    await screen.findByText("No notes yet.", { exact: false });
    await screen.findByRole("button", { name: "Mention a teammate" });

    await userEvent.type(composer(), "Call @");
    const picker = within(screen.getByRole("listbox", { name: "Mention someone on this position's team" }));
    expect(picker.getAllByRole("option").map((option) => option.textContent)).toEqual([
      "OKSA@teamEveryone on this position",
      "OKOmar KhalilLead",
      "SASara Al-MansourResearcher",
    ]);
    expect(screen.getByText(/never what the note says/)).toBeInTheDocument();

    await userEvent.type(composer(), "sa");
    expect(picker.getAllByRole("option").map((option) => option.textContent)).toEqual(["SASara Al-MansourResearcher"]);
    await userEvent.click(picker.getByRole("option", { name: /Sara Al-Mansour/ }));

    expect(composer()).toHaveValue("Call @Sara Al-Mansour ");
    expect(screen.getByText("Notifies").parentElement).toHaveTextContent("@Sara Al-Mansour");

    await userEvent.type(composer(), "please");
    await userEvent.click(screen.getByRole("button", { name: "Save note" }));

    await waitFor(() =>
      expect(personCrmApi.writePersonNote).toHaveBeenCalledWith("p1", "c1", {
        kind: "general",
        body: "Call @[Sara Al-Mansour](user:u3) please",
      }),
    );
  });

  it("mentions the whole team from the @ button, and a removed chip notifies nobody", async () => {
    renderNotes();
    await userEvent.click(await screen.findByRole("button", { name: "Mention a teammate" }));
    await userEvent.click(screen.getByRole("option", { name: /@team/ }));

    expect(composer()).toHaveValue("@team ");
    await userEvent.click(screen.getByRole("button", { name: "Remove @team" }));
    expect(screen.queryByText("Notifies")).not.toBeInTheDocument();

    await userEvent.type(composer(), "sync");
    await userEvent.click(screen.getByRole("button", { name: "Save note" }));

    await waitFor(() =>
      expect(personCrmApi.writePersonNote).toHaveBeenCalledWith("p1", "c1", { kind: "general", body: "@team sync" }),
    );
  });

  it("draws a saved note's mentions as chips, never the token", async () => {
    vi.mocked(personCrmApi.getPersonNotes).mockResolvedValue([
      aNote("Ask @[Sara Al-Mansour](user:u3) to call, cc @[team](team)"),
    ]);
    renderNotes();

    const note = await screen.findByText(/^Ask/);
    expect(note).toHaveTextContent("Ask SA@Sara Al-Mansour to call, cc @team");
    expect(within(note).getByTitle("Sara Al-Mansour")).toBeInTheDocument();
    expect(screen.queryByText(/\(user:/)).not.toBeInTheDocument();
  });

  it("files a linked follow-up for the owner chosen, once the note is saved", async () => {
    vi.mocked(tasksApi.createTask).mockResolvedValue({ id: "t1" } as TaskResponse);
    renderNotes();
    await screen.findByRole("button", { name: "Mention a teammate" });

    await userEvent.type(composer(), "Spoke to her about the move");
    await userEvent.click(screen.getByRole("checkbox", { name: /Create a linked follow-up/ }));
    await userEvent.type(screen.getByRole("textbox", { name: "Follow-up task" }), "Chase references");
    const owner = screen.getByRole("button", { name: /^Owner/ });
    expect(owner).toHaveAccessibleName("Owner Me · Yara Haddad");
    await userEvent.click(owner);
    await userEvent.click(screen.getByRole("option", { name: /Sara Al-Mansour/ }));
    await userEvent.click(screen.getByRole("button", { name: "Save note & follow-up" }));

    await waitFor(() =>
      expect(tasksApi.createTask).toHaveBeenCalledWith("p1", "c1", {
        title: "Chase references",
        kind: "follow_up",
        assigneeUserId: "u3",
        dueAt: new Date(2026, 9, 4, 9, 0).toISOString(),
        noteId: "n1",
      }),
    );
    expect(await screen.findByText("Note saved with a linked follow-up")).toBeInTheDocument();
  });
});
