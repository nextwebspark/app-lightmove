import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ComponentProps } from "react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../components/ui";
import { editorOf, typeInto } from "../../../test/noteEditor";
import * as projectsApi from "../../projects/api/projectsApi";
import type { Project, TeamMember } from "../../projects/api/types";
import * as personCrmApi from "../api/personCrmApi";
import type { PersonNote } from "../api/types";
import { NotesSection, PositionNoteComposer } from "./PersonSections";

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
  revisePersonNote: vi.fn(),
  removePersonNote: vi.fn(),
  restorePersonNote: vi.fn(),
  pinPersonNote: vi.fn(),
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
  format: "plain",
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
    vi.useFakeTimers({ toFake: ["Date"], shouldAdvanceTime: true });
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

    await typeInto(composer(), "Call @");
    const picker = within(await screen.findByRole("listbox", { name: "Mention someone on this position's team" }));
    expect(picker.getAllByRole("option").map((option) => option.textContent)).toEqual([
      "OKSA@teamEveryone on this position",
      "OKOmar KhalilLead",
      "SASara Al-MansourResearcher",
    ]);
    expect(screen.getByText(/never what the note says/)).toBeInTheDocument();

    await typeInto(composer(), "sa");
    await waitFor(() =>
      expect(picker.getAllByRole("option").map((option) => option.textContent)).toEqual([
        "SASara Al-MansourResearcher",
      ]),
    );
    await userEvent.click(picker.getByRole("option", { name: /Sara Al-Mansour/ }));

    expect(screen.getByText("Notifies").parentElement).toHaveTextContent("@Sara Al-Mansour");

    await typeInto(composer(), "please");
    await userEvent.click(screen.getByRole("button", { name: "Save note" }));

    await waitFor(() =>
      expect(personCrmApi.writePersonNote).toHaveBeenCalledWith("p1", "c1", {
        kind: "general",
        body: "Call @[Sara Al-Mansour](user:u3) please",
        format: "markdown",
      }),
    );
  });

  it("mentions the whole team from the @ button, and a removed chip notifies nobody", async () => {
    renderNotes();
    await userEvent.click(await screen.findByRole("button", { name: "Mention a teammate" }));
    await userEvent.click(await screen.findByRole("option", { name: /@team/ }));

    expect(screen.getByText("Notifies").parentElement).toHaveTextContent("@team");
    await userEvent.click(screen.getByRole("button", { name: "Remove @team" }));
    expect(screen.queryByText("Notifies")).not.toBeInTheDocument();

    await typeInto(composer(), "sync");
    await userEvent.click(screen.getByRole("button", { name: "Save note" }));

    await waitFor(() =>
      expect(personCrmApi.writePersonNote).toHaveBeenCalledWith("p1", "c1", {
        kind: "general",
        body: "sync",
        format: "markdown",
      }),
    );
  });

  it("keeps a note on this position unless it is for all positions, which mentions nobody", async () => {
    vi.mocked(personCrmApi.writePersonNote).mockImplementation(async (_p, _c, note) => ({
      ...aNote(note.body, note.kind),
      projectId: note.allPositions ? null : "p1",
      projectTitle: note.allPositions ? null : "Chief Financial Officer",
    }));
    renderNotes();
    await screen.findByRole("button", { name: "Mention a teammate" });
    const scope = within(screen.getByRole("radiogroup", { name: "Who reads this note" }));
    expect(scope.getByRole("radio", { name: "This position" })).toBeChecked();

    await typeInto(composer(), "Call @");
    await userEvent.click(await screen.findByRole("option", { name: /Sara Al-Mansour/ }));
    await userEvent.click(scope.getByRole("radio", { name: "All positions" }));

    expect(screen.queryByRole("button", { name: "Mention a teammate" })).not.toBeInTheDocument();
    await waitFor(() => expect(screen.queryByText("Notifies")).not.toBeInTheDocument());
    await typeInto(composer(), "back about any role");
    await userEvent.click(screen.getByRole("button", { name: "Save note" }));

    await waitFor(() =>
      expect(personCrmApi.writePersonNote).toHaveBeenCalledWith("p1", "c1", {
        kind: "general",
        body: "Call @Sara Al-Mansour back about any role",
        format: "markdown",
        allPositions: true,
      }),
    );
    expect(await screen.findByText("Note saved — your team sees it on every position")).toBeInTheDocument();
  });

  it("never repeats the position it is read from, and says when a note is for all positions", async () => {
    vi.mocked(personCrmApi.getPersonNotes).mockResolvedValue([
      aNote("Wants Dubai"),
      { ...aNote("Prefers a listed company"), id: "n2", projectId: null, projectTitle: null },
    ]);
    renderNotes();

    expect(await screen.findByText("All positions", { selector: "span" })).toBeInTheDocument();
    expect(screen.getByText("Wants Dubai")).toBeInTheDocument();
    expect(screen.queryByText("About Chief Financial Officer")).not.toBeInTheDocument();
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

  it("draws a formatted note's marks and lists", async () => {
    vi.mocked(personCrmApi.getPersonNotes).mockResolvedValue([
      { ...aNote("**Wants** Dubai\n\n- [ ] Ask for the CV"), format: "markdown" },
    ]);
    renderNotes();

    expect((await screen.findByText("Wants")).tagName).toBe("STRONG");
    expect(screen.getByRole("checkbox", { name: "Not done" })).not.toBeChecked();
  });

  it("saves the note, then hands it to the next action by kind and date", async () => {
    const onSetNextAction = vi.fn();
    renderNotes({ onSetNextAction });
    await screen.findByRole("button", { name: "Mention a teammate" });

    expect(screen.queryByRole("radiogroup", { name: "Kind of note" })).not.toBeInTheDocument();
    await typeInto(composer(), "Wants to speak to her family first");
    await userEvent.click(screen.getByRole("button", { name: "Save & set next action" }));

    await waitFor(() =>
      expect(onSetNextAction).toHaveBeenCalledWith({ id: "n1", kind: "general", writtenAt: "2026-10-03T08:00:00Z" }),
    );
    expect(personCrmApi.writePersonNote).toHaveBeenCalledWith("p1", "c1", {
      kind: "general",
      body: "Wants to speak to her family first",
      format: "markdown",
    });
    await waitFor(() => expect(composer()).toHaveTextContent(""));
  });

  it("offers Save note alone where nobody takes the next action", async () => {
    renderNotes();
    await typeInto(await screen.findByRole("textbox", { name: "New note" }), "Background only");

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

  it("holds a note past the limit", async () => {
    renderNotes();
    const box = await screen.findByRole("textbox", { name: "New note" });

    await act(async () => {
      editorOf(box).commands.insertContent("x".repeat(10001));
    });

    expect(await screen.findByRole("alert")).toHaveTextContent("Too long to save");
    expect(screen.getByRole("button", { name: "Save note" })).toBeDisabled();
  });
});

describe("a note in the position's drawer", () => {
  beforeEach(() => {
    vi.mocked(personCrmApi.getPersonNotes).mockResolvedValue([aNote("Prefers a call after 6pm")]);
    vi.mocked(personCrmApi.getPersonPositions).mockResolvedValue([]);
    vi.mocked(projectsApi.projects).mockResolvedValue([position]);
  });

  it("is edited in place by its author", async () => {
    vi.mocked(personCrmApi.revisePersonNote).mockImplementation(async (_p, _c, _n, note) => aNote(note.body));
    renderNotes();

    await userEvent.click(await screen.findByRole("button", { name: "Edit" }));
    const editor = await screen.findByRole("textbox", { name: "Edit note" });
    expect(editor).toHaveTextContent("Prefers a call after 6pm");
    await act(async () => {
      editorOf(editor).commands.setContent("<p>Prefers a call after <strong>7pm</strong></p>");
    });
    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() =>
      expect(personCrmApi.revisePersonNote).toHaveBeenCalledWith("p1", "c1", "n1", {
        kind: "general",
        body: "Prefers a call after **7pm**",
        format: "markdown",
      }),
    );
  });

  it("saved untouched sends nothing back, so a plain note's own '*' and '1.' never turn into formatting", async () => {
    vi.mocked(personCrmApi.getPersonNotes).mockResolvedValue([aNote("Spoke *twice*\n1. wants Dubai")]);
    vi.mocked(personCrmApi.revisePersonNote).mockClear();
    renderNotes();

    await userEvent.click(await screen.findByRole("button", { name: "Edit" }));
    await screen.findByRole("textbox", { name: "Edit note" });
    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    expect(screen.queryByRole("textbox", { name: "Edit note" })).not.toBeInTheDocument();
    expect(personCrmApi.revisePersonNote).not.toHaveBeenCalled();
  });

  it("takes a link without saving the note under it", async () => {
    vi.mocked(personCrmApi.revisePersonNote).mockClear();
    renderNotes();

    await userEvent.click(await screen.findByRole("button", { name: "Edit" }));
    const editor = await screen.findByRole("textbox", { name: "Edit note" });
    await act(async () => {
      editorOf(editor).commands.selectAll();
    });
    const card = editor.closest("li") as HTMLElement;
    await userEvent.click(within(card).getByRole("button", { name: "Link" }));
    await userEvent.type(within(card).getByRole("textbox", { name: "Link address" }), "example.com{Enter}");

    expect(screen.getByRole("textbox", { name: "Edit note" })).toBeInTheDocument();
    expect(personCrmApi.revisePersonNote).not.toHaveBeenCalled();
    expect(editorOf(editor).getAttributes("link").href).toBe("https://example.com");
  });

  it("is pinned by anyone on the position", async () => {
    vi.mocked(personCrmApi.getPersonNotes).mockResolvedValue([{ ...aNote("Board seat in 2027"), editable: false }]);
    vi.mocked(personCrmApi.pinPersonNote).mockResolvedValue(aNote("Board seat in 2027"));
    renderNotes();

    await userEvent.click(await screen.findByRole("button", { name: "Pin" }));

    await waitFor(() => expect(personCrmApi.pinPersonNote).toHaveBeenCalledWith("p1", "c1", "n1", true));
    expect(screen.queryByRole("button", { name: "Edit" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Delete" })).not.toBeInTheDocument();
  });

  it("is deleted once confirmed, and Undo brings it back", async () => {
    vi.mocked(personCrmApi.removePersonNote).mockResolvedValue(undefined);
    vi.mocked(personCrmApi.restorePersonNote).mockResolvedValue(aNote("Prefers a call after 6pm"));
    renderNotes();

    await userEvent.click(await screen.findByRole("button", { name: "Delete" }));
    await userEvent.click(await screen.findByRole("button", { name: "Delete note" }));
    await waitFor(() => expect(personCrmApi.removePersonNote).toHaveBeenCalledWith("p1", "c1", "n1"));
    await userEvent.click(await screen.findByRole("button", { name: "Undo" }));

    await waitFor(() => expect(personCrmApi.restorePersonNote).toHaveBeenCalledWith("p1", "c1", "n1"));
  });

  it("is kept when the delete is cancelled", async () => {
    vi.mocked(personCrmApi.removePersonNote).mockClear();
    renderNotes();

    await userEvent.click(await screen.findByRole("button", { name: "Delete" }));
    await userEvent.click(await screen.findByRole("button", { name: "Cancel" }));

    expect(screen.queryByRole("button", { name: "Delete note" })).not.toBeInTheDocument();
    expect(personCrmApi.removePersonNote).not.toHaveBeenCalled();
  });

  it("cannot be deleted twice while the list catches up", async () => {
    vi.mocked(personCrmApi.removePersonNote).mockClear().mockResolvedValue(undefined);
    vi.mocked(personCrmApi.restorePersonNote).mockResolvedValue(aNote("Prefers a call after 6pm"));
    renderNotes();

    await userEvent.click(await screen.findByRole("button", { name: "Delete" }));
    await userEvent.click(await screen.findByRole("button", { name: "Delete note" }));
    await screen.findByRole("button", { name: "Undo" });

    expect(screen.getByRole("button", { name: "Delete" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Edit" })).toBeDisabled();
    expect(personCrmApi.removePersonNote).toHaveBeenCalledTimes(1);

    await userEvent.click(screen.getByRole("button", { name: "Undo" }));
    await waitFor(() => expect(screen.getByRole("button", { name: "Delete" })).toBeEnabled());
  });
});

describe("a log: a call, a LinkedIn message or a meeting", () => {
  const coffee = {
    id: "mt1",
    title: "Coffee at DIFC",
    startsAt: "2026-09-28T06:00:00Z",
    endsAt: "2026-09-28T07:00:00Z",
    ownerUserId: "u1",
    ownerName: "Yara Haddad",
    ownerAvatarUrl: null,
    joinUrl: null,
    videoProvider: null,
    viaLink: false,
    bookedInUncava: false,
    editable: false,
  };

  beforeEach(() => {
    vi.useFakeTimers({ toFake: ["Date"], shouldAdvanceTime: true });
    vi.setSystemTime(new Date(2026, 9, 3, 10, 0));
    vi.mocked(personCrmApi.getPersonPositions).mockResolvedValue([]);
    vi.mocked(personCrmApi.writePersonNote).mockImplementation(async (_p, _c, note) => aNote(note.body, note.kind));
    vi.mocked(projectsApi.projects).mockResolvedValue([position]);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  function renderLog(props: Partial<ComponentProps<typeof PositionNoteComposer>>) {
    render(
      <MemoryRouter>
        <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
          <ToastProvider>
            <PositionNoteComposer
              projectId="p1"
              candidateId="c1"
              kindChoices={["call", "meeting", "linkedin"]}
              {...props}
            />
          </ToastProvider>
        </QueryClientProvider>
      </MemoryRouter>,
    );
  }

  it("is stamped now unless changed, and carries the title typed", async () => {
    renderLog({ initialKind: "call" });
    await screen.findByRole("button", { name: "Mention a teammate" });

    await userEvent.click(screen.getByRole("button", { name: "When it happened: Today, 10:00" }));
    expect(screen.getByLabelText("Time it happened")).toHaveValue("10:00");
    await userEvent.type(screen.getByRole("textbox", { name: "Title (optional)" }), "First call");
    await typeInto(composer(), "Open to a move.");
    await userEvent.click(screen.getByRole("button", { name: "Save note" }));

    await waitFor(() =>
      expect(personCrmApi.writePersonNote).toHaveBeenCalledWith("p1", "c1", {
        kind: "call",
        body: "Open to a move.",
        format: "markdown",
        occurredAt: new Date(2026, 9, 3, 10, 0).toISOString(),
        title: "First call",
        meetingId: null,
        recordingId: null,
      }),
    );
  });

  it("ties a meeting's notes to the meeting chosen, which brings its own time", async () => {
    renderLog({ initialKind: "meeting", pastMeetings: [coffee], presetMeetingId: "mt1" });
    await screen.findByRole("button", { name: "Mention a teammate" });

    expect(screen.getByRole("button", { name: "Which meeting: Coffee at DIFC" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /^When it happened/ })).not.toBeInTheDocument();
    await typeInto(composer(), "Wants a listed group.");
    await userEvent.click(screen.getByRole("button", { name: "Save note" }));

    await waitFor(() =>
      expect(personCrmApi.writePersonNote).toHaveBeenCalledWith("p1", "c1", {
        kind: "meeting",
        body: "Wants a listed group.",
        format: "markdown",
        meetingId: "mt1",
        recordingId: null,
        title: null,
        occurredAt: null,
      }),
    );
  });

  it("names the recording of a call the calendar no longer holds", async () => {
    const recorded = { ...coffee, id: "recording:r1", ownerUserId: "", ownerName: null };
    renderLog({ initialKind: "meeting", pastMeetings: [recorded], presetMeetingId: "recording:r1" });
    await screen.findByRole("button", { name: "Mention a teammate" });

    await typeInto(composer(), "She asked about the board.");
    await userEvent.click(screen.getByRole("button", { name: "Save note" }));

    await waitFor(() =>
      expect(personCrmApi.writePersonNote).toHaveBeenCalledWith("p1", "c1", expect.objectContaining({
        kind: "meeting",
        meetingId: null,
        recordingId: "r1",
      })),
    );
  });

  it("keeps the meeting it opened on for the next note, after saving one", async () => {
    renderLog({ initialKind: "meeting", pastMeetings: [coffee], presetMeetingId: "mt1" });
    await screen.findByRole("button", { name: "Mention a teammate" });

    await typeInto(composer(), "First thought.");
    await userEvent.click(screen.getByRole("button", { name: "Save note" }));
    await waitFor(() => expect(personCrmApi.writePersonNote).toHaveBeenCalled());

    await waitFor(() =>
      expect(screen.getByRole("button", { name: "Which meeting: Coffee at DIFC" })).toBeInTheDocument(),
    );
  });

  it("says a time later today has not happened yet, and does not save it", async () => {
    renderLog({ initialKind: "call" });
    await screen.findByRole("button", { name: "Mention a teammate" });

    await userEvent.click(screen.getByRole("button", { name: /^When it happened/ }));
    const time = screen.getByLabelText("Time it happened");
    await userEvent.clear(time);
    await userEvent.type(time, "18:00");
    await typeInto(composer(), "Spoke later.");

    expect(screen.getByRole("alert")).toHaveTextContent("This time hasn’t happened yet.");
    expect(screen.getByRole("button", { name: "Save note" })).toBeDisabled();
  });

  it("asks a call when it happened, never which meeting it was", async () => {
    renderLog({ initialKind: "call", pastMeetings: [coffee] });
    await screen.findByRole("button", { name: "Mention a teammate" });

    expect(screen.queryByRole("button", { name: /^Which meeting/ })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "When it happened: Today, 10:00" })).toBeInTheDocument();
  });

  it("takes a date and time for a meeting held outside Uncava", async () => {
    renderLog({ initialKind: "meeting", pastMeetings: [coffee] });
    await screen.findByRole("button", { name: "Mention a teammate" });

    expect(screen.getByRole("button", { name: "Which meeting: Outside Uncava" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "When it happened: Today, 10:00" })).toBeInTheDocument();
  });

  it("picks a meeting from the calendar list, which then brings its own time", async () => {
    renderLog({ initialKind: "meeting", pastMeetings: [coffee] });
    await screen.findByRole("button", { name: "Mention a teammate" });

    await userEvent.click(screen.getByRole("button", { name: "Which meeting: Outside Uncava" }));
    const list = screen.getByRole("listbox", { name: "Which meeting" });
    expect(within(list).getByRole("option", { name: /Outside Uncava/ })).toHaveAttribute("aria-selected", "true");
    await userEvent.click(within(list).getByRole("option", { name: /Coffee at DIFC/ }));

    expect(screen.getByRole("button", { name: "Which meeting: Coffee at DIFC" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /^When it happened/ })).not.toBeInTheDocument();
  });

  it("stamps a quick time picked from the When chip", async () => {
    renderLog({ initialKind: "call" });
    await screen.findByRole("button", { name: "Mention a teammate" });

    await userEvent.click(screen.getByRole("button", { name: "When it happened: Today, 10:00" }));
    await userEvent.click(screen.getByRole("button", { name: "1 hour ago" }));
    expect(screen.getByRole("button", { name: "When it happened: Today, 09:00" })).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "When it happened: Today, 09:00" }));
    await userEvent.click(screen.getByRole("button", { name: "Yesterday, same time" }));
    expect(screen.getByRole("button", { name: "When it happened: Yesterday, 10:00" })).toBeInTheDocument();
  });
});
