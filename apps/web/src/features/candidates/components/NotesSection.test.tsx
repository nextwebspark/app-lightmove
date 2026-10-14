import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ComponentProps } from "react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import * as projectsApi from "../../projects/api/projectsApi";
import type { Project, TeamMember } from "../../projects/api/types";
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

const aNote = (body: string, kind: PersonNote["kind"] = "general"): PersonNote => ({
  id: "n1",
  kind,
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

function renderNotes(props: Partial<ComponentProps<typeof NotesSection>> = {}) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const tree = (more: Partial<ComponentProps<typeof NotesSection>>) => (
    <MemoryRouter>
      <QueryClientProvider client={client}>
        <ToastProvider>
          <NotesSection projectId="p1" candidateId="c1" {...more} />
        </ToastProvider>
      </QueryClientProvider>
    </MemoryRouter>
  );
  const view = render(tree(props));
  return { rerender: (more: Partial<ComponentProps<typeof NotesSection>>) => view.rerender(tree(more)) };
}

const composer = () => screen.getByRole("textbox", { name: "New note" });

describe("the note composer", () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date(2026, 9, 3, 10, 0));
    vi.mocked(personCrmApi.getPersonNotes).mockResolvedValue([]);
    vi.mocked(personCrmApi.getPersonPositions).mockResolvedValue([]);
    vi.mocked(personCrmApi.writePersonNote).mockImplementation(async (_p, _c, note) => aNote(note.body, note.kind));
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

  it("saves the note, then hands it to the next action by kind and date", async () => {
    const onSetNextAction = vi.fn();
    renderNotes({ onSetNextAction });
    await screen.findByRole("button", { name: "Mention a teammate" });

    await userEvent.click(screen.getByRole("radio", { name: "Call" }));
    await userEvent.type(composer(), "Wants to speak to her family first");
    await userEvent.click(screen.getByRole("button", { name: "Save & set next action" }));

    await waitFor(() =>
      expect(onSetNextAction).toHaveBeenCalledWith({ id: "n1", kind: "call", writtenAt: "2026-10-03T08:00:00Z" }),
    );
    expect(personCrmApi.writePersonNote).toHaveBeenCalledWith("p1", "c1", {
      kind: "call",
      body: "Wants to speak to her family first",
    });
    expect(composer()).toHaveValue("");
  });

  it("offers Save note alone where nobody takes the next action", async () => {
    renderNotes();
    await userEvent.type(await screen.findByRole("textbox", { name: "New note" }), "Background only");

    expect(screen.getByRole("button", { name: "Save note" })).toBeEnabled();
    expect(screen.queryByRole("button", { name: "Save & set next action" })).not.toBeInTheDocument();
  });

  it("brings the note a task was set from into view", async () => {
    const scrollIntoView = vi.fn();
    Element.prototype.scrollIntoView = scrollIntoView;
    vi.mocked(personCrmApi.getPersonNotes).mockResolvedValue([aNote("Follow up after Q3 close")]);
    const { rerender } = renderNotes({ focus: { noteId: "n1", at: 1 } });

    const card = (await screen.findByText("Follow up after Q3 close")).closest("li") as HTMLElement;
    await waitFor(() => expect(scrollIntoView).toHaveBeenCalledWith({ block: "center", behavior: "smooth" }));
    expect(card).toHaveFocus();
    expect(card.querySelector(".animate-note-flash")).not.toBeNull();

    rerender({ focus: null });

    expect(screen.getByText("Follow up after Q3 close").closest("li")).toBe(card);
    expect(card).toHaveFocus();
  });

  it("holds a note that its mentions would carry past the limit", async () => {
    renderNotes();
    await screen.findByRole("button", { name: "Mention a teammate" });

    await userEvent.type(composer(), "Call @sa");
    await userEvent.click(screen.getByRole("option", { name: /Sara Al-Mansour/ }));
    await userEvent.paste("x".repeat(3970));

    expect(await screen.findByRole("alert")).toHaveTextContent("Too long once its mentions are saved");
    expect(screen.getByRole("button", { name: "Save note" })).toBeDisabled();
  });
});
